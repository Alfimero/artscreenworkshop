package artscreen;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.day;
import static processing.core.PApplet.dist;
import static processing.core.PApplet.hour;
import static processing.core.PApplet.minute;
import static processing.core.PApplet.month;
import static processing.core.PApplet.round;
import static processing.core.PApplet.second;
import static processing.core.PApplet.year;
import static processing.core.PConstants.ALPHA;
import static processing.core.PConstants.RGB;
import static processing.core.PConstants.RIGHT;
import static processing.core.PConstants.TOP;

import java.awt.Rectangle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// https://github.com/atduskgreg/opencv-processing
import gab.opencv.OpenCV;
import largesketchviewer.LargeSketchViewer;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PVector;
import processing.event.KeyEvent;
// https://processing.org/reference/libraries/video/Capture.html
import processing.video.Capture;

/**
 * ArtScreen is required to publish your art to
 * the interactive screen.
 * 
 * @example Empty
 */
public class ArtScreen {
	public static final String VERSION = "##library.prettyVersion##";
	private static final int DEFAULT_DURATION = 60 * 1000;
	
	private final PApplet p;
	private final String titleOfArtwork, artistFullName, additionalCredits;
	private final int captionTextColor, captionBackgroundColor;
	private final int duration;
	private final int saveFrameAtMillis;
	
	private boolean saved = false;
	private boolean debug = false;
	
	// Captions
	private static final int CAPTION_HEIGHT = 80;
	private static final int CAPTION_MARGIN_RIGHT = 30;
	private static final int CAPTION_MARGIN_TOP = 8;
	private static final int CAPTION_LINE_HEIGHT = 24;
	private static final int CAPTION_Y_OFFSET = 0;
	public final PFont openSansSemiBold22;
	public final PFont openSansSemiBoldItalic22;
	public final PFont openSansSemiBold16;
	
	// Video Processing
	private static final int THRESHOLD = 80;
	private Capture video; // processing video capture
	private static final int CAPTURE_WIDTH = 1280;
	private static final int CAPTURE_HEIGHT = 720;
	private static final float CAPTURE_FPS = 30;
	private PImage pImage; // previous frame
	
	private final OpenCV opencv;
	private static final int IMG_PROCESSING_W = CAPTURE_WIDTH / 2;
	private static final int IMG_PROCESSING_H = CAPTURE_HEIGHT / 2;
	private final ExecutorService opencvProcessingThread = Executors.newFixedThreadPool(1);
	private boolean openCVReady = true;
	
	private final float screenToCaptureRatioWidth, screenToCaptureRatioHeight;
	private final float screenToOpenCVRatioWidth, screenToOpenCVRatioHeight;
	
	private final PGraphics pgForSavingScreen;
	
	// Public variables sketches should access
	public Face[] faces = new Face[] {}; // initially empty, no faces
	public PImage motionImage;
	public PImage motionImageFull;
	public boolean movementDetected = false;
	public PVector maxMotionLocation = new PVector(0, 0);
	public MotionPixel[] top100MotionPixels = new MotionPixel[] {};
	
	public ArtScreen(PApplet p, String titleOfArtwork, String artistFullName, String additionalCredits, int captionTextColor, int captionBackgroundColor) {
		this.p = p;
		this.titleOfArtwork = titleOfArtwork;
		this.artistFullName = artistFullName;
		this.additionalCredits = additionalCredits;
		this.captionTextColor = captionTextColor;
		this.captionBackgroundColor = captionBackgroundColor;
		p.registerMethod("pre", this);
		p.registerMethod("draw", this);
		p.registerMethod("post", this);
		p.registerMethod("dispose", this);
		p.registerMethod("keyEvent", this);
		
		if (p.args != null && p.args.length != 0 && p.args[0].equals("live")) {
			// no preview
		} else {
			LargeSketchViewer.smallPreview(p, false, 15, true); // show smaller preview
		}
		duration = getDuration(p);
		
		// OpenCV face detection
		opencv = new OpenCV(p, IMG_PROCESSING_W, IMG_PROCESSING_H); // do image processing on smaller image
		opencv.loadCascade(OpenCV.CASCADE_FRONTALFACE);
		
		video = new Capture(p, CAPTURE_WIDTH, CAPTURE_HEIGHT, (int) CAPTURE_FPS);
		video.start(); // if on processing 151, comment this line 
		
		screenToCaptureRatioWidth = p.width / (float) CAPTURE_WIDTH;
		screenToCaptureRatioHeight = p.height / (float) CAPTURE_HEIGHT;
		
		screenToOpenCVRatioWidth = p.width / (float) IMG_PROCESSING_W;
		screenToOpenCVRatioHeight = p.height / (float) IMG_PROCESSING_H;
		
		/* for saving frames, for documentation */
		saveFrameAtMillis = (int) (duration / 2.0); // safe frame mid-way through our run
		pgForSavingScreen = p.createGraphics(p.width, p.height);
		
		motionImage = p.createImage(CAPTURE_WIDTH, CAPTURE_HEIGHT, ALPHA);
		pImage = p.createImage(CAPTURE_WIDTH, CAPTURE_HEIGHT, RGB);
		
		openSansSemiBold22 = p.loadFont("OpenSans-Semibold-22.vlw");
		openSansSemiBoldItalic22 = p.loadFont("OpenSans-SemiboldItalic-22.vlw");
		openSansSemiBold16 = p.loadFont("OpenSans-Semibold-16.vlw");
		
		p.pushStyle();
		p.colorMode(RGB, 255);
		p.background(0);
		drawArtworkCaption(titleOfArtwork, artistFullName, additionalCredits);
		p.popStyle();
		
		p.noCursor();
	}
	
	private int getDuration(PApplet p) {
		if (p.args != null && p.args.length >= 2) {
			try {
				int requestedDuration = Integer.parseInt(p.args[1]);
				if (requestedDuration > 1000) {
					return requestedDuration;
				}
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		return DEFAULT_DURATION;
	}
	
	// Method that's called just after beginDraw(), meaning that it can affect drawing.
	public void pre() {
		if (video.available() == true) {
			video.read();
			motionUpdates();
			
			/*
			 * make a copy of the video frame, scaled down to the small size we want for image processing
			 * work with a different copy, in case the thread is processing the current copy
			 * only process on occasion, it can be slow
			 */
			try {
				if (openCVReady) {
					openCVReady = false;
					PImage newOpenCVProcessingFrame = p.createImage(IMG_PROCESSING_W, IMG_PROCESSING_H, RGB);
					newOpenCVProcessingFrame.copy(video, 0, 0, video.width, video.height, 0, 0, IMG_PROCESSING_W, IMG_PROCESSING_H);
					opencvProcessingThread.execute(new Runnable() {
						public void run() {
							detectFaces(newOpenCVProcessingFrame);
						}
					});
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}
	
	// Method that's called at the end of draw(), but before endDraw().
	public void draw() {
		if (p.millis() >= duration) {
			// enough time has passed, exit Sketch
			// so the next Sketch may start
			p.exit();
		}
		
		p.pushMatrix();
		p.resetMatrix();
		drawArtworkCaption(titleOfArtwork, artistFullName, additionalCredits);
		drawDebugInfo();
		p.popMatrix();
	}
	
	// Called when a key event occurs in the parent applet. 
	// Drawing is allowed because key events are queued, unless the sketch has called noLoop().
	public void keyEvent(KeyEvent e) {
		switch (e.getAction()) {
			case KeyEvent.RELEASE:
				debug = !debug;
				break;
		}
	}
	
	/* run openCV face detection on the current video frame */
	private void detectFaces(PImage frameToProcess) {
		opencv.loadImage(frameToProcess);
		Rectangle[] facesRectangles = opencv.detect();
		// convert to actually screen coordinates
		Face[] newFaces = new Face[facesRectangles.length];
		for (int i = 0; i < newFaces.length; i++) {
			Rectangle faceRect = facesRectangles[i];
			// scale and mirror
			PVector newLoc = new PVector(p.width - faceRect.x * screenToOpenCVRatioWidth - faceRect.width, faceRect.y * screenToOpenCVRatioHeight);
			newFaces[i] = new Face(newLoc, faceRect.width * screenToOpenCVRatioWidth, faceRect.height * screenToOpenCVRatioHeight);
		}
		faces = newFaces;
		openCVReady = true;
	}
	
	// Method called after draw has completed and the frame is done. No drawing allowed.
	public void post() {
		if (!saved && p.millis() > saveFrameAtMillis) {
			saved = true;
			pgForSavingScreen.beginDraw();
			pgForSavingScreen.loadPixels();
			p.loadPixels();
			for (int x = 0; x < p.width; x++) {
				for (int y = 0; y < p.height; y++) {
					/*
					 * copy current display to our staging graphics
					 * mirroring the image in the process
					 * https://processing.org/discourse/beta/num_1220788246.html
					 */
					pgForSavingScreen.pixels[y * p.width + x] = p.pixels[(p.width - x - 1) + y * p.width]; // Reversing x to mirror the image
				}
			}
			
			pgForSavingScreen.updatePixels();
			pgForSavingScreen.save("saved/" + year() + "-" + month() + "-" + day() + "_" + hour() + "-" + minute() + "-" + second() + "_" + getClass().getSimpleName() + ".png");
			pgForSavingScreen.endDraw();
		}
	}
	
	// Anything in here will be called automatically when 
	// the parent sketch shuts down.
	public void dispose() {
		opencvProcessingThread.shutdownNow();
		video.stop();
		video = null;
	}
	
	private void drawArtworkCaption(String titleOfArtwork, String artistFullName, String additionalCredits) {
		float yTop = p.height - CAPTION_HEIGHT - CAPTION_Y_OFFSET;
		float captionTop = yTop + CAPTION_MARGIN_TOP;
		
		p.pushStyle();
		p.pushMatrix();
		p.colorMode(RGB, 255);
		p.translate(p.width, 0);
		p.scale(-1, 1);
		p.noStroke();
		p.fill(captionBackgroundColor);
		p.rect(p.width * 2f / 3f, yTop, p.width / 3f, CAPTION_HEIGHT);
		
		p.fill(captionTextColor);
		p.textAlign(RIGHT, TOP);
		p.textFont(openSansSemiBoldItalic22);
		p.text("“" + titleOfArtwork + "”", p.width - CAPTION_MARGIN_RIGHT, captionTop);
		p.textFont(openSansSemiBold22);
		p.text(artistFullName, p.width - CAPTION_MARGIN_RIGHT, captionTop + CAPTION_LINE_HEIGHT);
		p.textFont(openSansSemiBold16);
		p.text(additionalCredits, p.width - CAPTION_MARGIN_RIGHT, captionTop + 2 * CAPTION_LINE_HEIGHT);
		p.popMatrix();
		p.popStyle();
	}
	
	private void drawDebugInfo() {
		// draw change amount at every motion pixel
		for (MotionPixel motionPixel : top100MotionPixels) {
			p.text(motionPixel.changeAmount, cameraXToScreen(motionPixel.location.x), cameraXToScreen(motionPixel.location.y));
		}
	}
	
	public float cameraXToScreen(float x) {
		return constrain((float) x * screenToCaptureRatioWidth, 0, p.width);
	}
	
	public float cameraYToScreen(float y) {
		return constrain((float) y * screenToCaptureRatioHeight, 0, p.height);
	}
	
	private void motionUpdates() {
		p.pushStyle();
		p.colorMode(RGB, 255);
		
		pImage.loadPixels();
		video.loadPixels();
		PImage newMotionImage = p.createImage(video.width, video.height, ALPHA); // Create an empty image for staging the image the same size as the video
		newMotionImage.loadPixels();
		
		MotionPixels motionPixels = new MotionPixels();
		
		float maxChange = 0;
		boolean newMotion = false;
		int newX = 0;
		int newY = 0;
		for (int x = 0; x < pImage.width; x++) {
			for (int y = 0; y < pImage.height; y++) {
				int loc = x + y * pImage.width; //1D pixel location
				int oldR = round(p.red(pImage.pixels[loc]));
				int oldG = round(p.green(pImage.pixels[loc]));
				int oldB = round(p.blue(pImage.pixels[loc]));
				int newR = round(p.red(video.pixels[loc]));
				int newG = round(p.green(video.pixels[loc]));
				int newB = round(p.blue(video.pixels[loc]));
				
				float change = dist(oldR, oldG, oldB, newR, newG, newB);
				if (change > THRESHOLD && change > maxChange) {
					newMotion = true;
					newX = x;
					newY = y;
				}
				byte changeB = (byte) constrain((int) (change), 0, 255);
				newMotionImage.pixels[loc] = p.color(changeB);
				motionPixels.add(new MotionPixel(new PVector(x, y), changeB));
			}
		}
		
		top100MotionPixels = motionPixels.toArray();
		
		newMotionImage.updatePixels();
		motionImage = newMotionImage;
		
		movementDetected = newMotion;
		float motionPixelX = constrain(round((float) newX * screenToCaptureRatioWidth), 0, p.width);
		float motionPixelY = constrain(round((float) newY * screenToCaptureRatioHeight), 0, p.height);
		maxMotionLocation = new PVector(motionPixelX, motionPixelY);
		
		// save current frame to old
		pImage.copy(video, 0, 0, video.width, video.height, 0, 0, pImage.width, pImage.height);
		
		p.popStyle();
	}
	
	/**
	 * return the version of the Library.
	 * 
	 * @return String
	 */
	public static String version() {
		return VERSION;
	}
}
