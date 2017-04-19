package artscreen;

import static processing.core.PApplet.constrain;
import static processing.core.PApplet.println;
import static processing.core.PApplet.round;
import static processing.core.PConstants.RGB;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import largesketchviewer.LargeSketchViewer;
import processing.core.PApplet;
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
	
	ComputerVision computerVision;
	final ScreenCapture screenCapture;
	final Text text;
	final Debug debug;
	
	private final String titleOfArtwork, artistFullName, additionalCredits;
	private final int captionTextColor, captionBackgroundColor;
	
	private final int duration;
	
	// Video Processing
	private static final int DEFAULT_CAPTURE_WIDTH = 1280;
	private static final int DEFAULT_CAPTURE_HEIGHT = 720;
	private static final float DEFAULT_CAPTURE_FPS = 30;
	private static final int DEFAULT_MOTION_THRESHOLD = 80;
	
	private final Pattern processingCaptureWidthHeightMatcher = Pattern.compile(".*size=([0-9]+)x([0-9]+),.*");
	
	public static final int IMG_PROCESSING_W = DEFAULT_CAPTURE_WIDTH / 2;
	public static final int IMG_PROCESSING_H = DEFAULT_CAPTURE_HEIGHT / 2;
	
	// Public variables sketches should access
	public Face[] faces = new Face[] {}; // initially empty, no faces
	public PImage motionImage;
	public boolean movementDetected = false;
	public PVector maxMotionLocation = new PVector(0, 0);
	public MotionPixel[] top100MotionPixels = new MotionPixel[] {};
	public PImage camSmall;
	public PImage camSmallMirror;
	public Capture cam; // processing video capture
	public int captureWidth;
	public int captureHeight;
	
	public ArtScreen(PApplet p, String titleOfArtwork, String artistFullName, String additionalCredits, int captionTextColor, int captionBackgroundColor) {
		this(p, titleOfArtwork, artistFullName, additionalCredits, captionTextColor, captionBackgroundColor, DEFAULT_MOTION_THRESHOLD);
	}
	
	public ArtScreen(PApplet p, String titleOfArtwork, String artistFullName, String additionalCredits, int captionTextColor, int captionBackgroundColor, int motionThreshold) {
		this.p = p;
		this.titleOfArtwork = titleOfArtwork;
		this.artistFullName = artistFullName;
		this.additionalCredits = additionalCredits;
		this.captionTextColor = captionTextColor;
		this.captionBackgroundColor = captionBackgroundColor;
		
		duration = getDuration(p);
		text = new Text(p);
		debug = new Debug(this, p);
		screenCapture = new ScreenCapture(this, p, duration);
		
		String[] availableCameras = Capture.list();
		if (availableCameras.length == 0) {
			println("No cameras found. Exiting.");
			p.exit();
			return;
		}
		
		String requestedCamera = availableCameras[0];
		String partialCameraName = "size=" + DEFAULT_CAPTURE_WIDTH + "x" + DEFAULT_CAPTURE_HEIGHT + ",fps=" + DEFAULT_CAPTURE_FPS;
		for (String camera : availableCameras) {
			if (camera.contains(partialCameraName)) {
				requestedCamera = camera;
				break;
			}
		}
		cam = new Capture(p, requestedCamera);
		cam.start(); // if on processing 151, comment this line 
		
		Matcher matcher = processingCaptureWidthHeightMatcher.matcher(requestedCamera);
		if (!matcher.matches()) {
			throw new IllegalStateException("Unable to initialize camera");
		}
		captureWidth = Integer.valueOf(matcher.group(1));
		captureHeight = Integer.valueOf(matcher.group(2));
		
		camSmall = p.createImage(captureWidth / 4, captureHeight / 4, RGB);
		camSmallMirror = p.createImage(captureWidth / 4, captureHeight / 4, RGB);
		computerVision = new ComputerVision(this, p, captureWidth, captureHeight, motionThreshold);
		motionImage = p.createImage(captureWidth / 4, captureHeight / 4, RGB);
		
		if (p.args != null && p.args.length != 0 && p.args[0].equals("live")) {
			// no preview
		} else {
			LargeSketchViewer.smallPreview(p, false, 15, true); // show smaller preview
		}
		
		// draw black background
		p.pushStyle();
		p.colorMode(RGB, 255);
		p.background(0);
		p.popStyle();
		
		// draw our caption right away, before the sketch has loaded
		text.drawArtworkCaption(titleOfArtwork, artistFullName, additionalCredits, captionTextColor, captionBackgroundColor);
		
		p.noCursor(); // remove cursor icon
		
		p.registerMethod("pre", this);
		p.registerMethod("draw", this);
		p.registerMethod("post", this);
		p.registerMethod("dispose", this);
		p.registerMethod("keyEvent", this);
	}
	
	// Method that's called just after beginDraw(), meaning that it can affect drawing.
	public void pre() {
		if (p.millis() >= duration) {
			// enough time has passed, exit Sketch
			// so the next Sketch may start
			p.exit();
		}
		
		if (cam.available() == true) {
			cam.read();
			camSmall.copy(cam, 0, 0, cam.width, cam.height, 0, 0, camSmall.width, camSmall.height);
			
			// flip all pixels left-to-right, so our webcam behaves like a mirror, instead of a camera
			// http://stackoverflow.com/questions/29334348/processing-mirror-image-over-x-axis
			camSmall.loadPixels();
			camSmallMirror.loadPixels();
			for (int i = 0; i < camSmallMirror.pixels.length; i++) { //loop through each pixel
				int srcX = i % camSmallMirror.width; //calculate source(original) x position
				int dstX = camSmallMirror.width - srcX - 1; //calculate destination(flipped) x position = (maximum-x-1)
				int y = i / camSmallMirror.width; //calculate y coordinate
				camSmallMirror.pixels[y * camSmallMirror.width + dstX] = camSmall.pixels[i];//write the destination(x flipped) pixel based on the current pixel  
			}
			camSmallMirror.updatePixels();
			computerVision.performCalculations(camSmallMirror);
		}
		
		/*
		 * since we are rear-projecting our image will be
		 * mirrored, so we want to flip it, so text reads correctly
		 * and so that the user's motion is as expected
		 */
		p.scale(-1, 1);
		p.translate(-p.width, 0);
	}
	
	// Method that's called at the end of draw(), but before endDraw().
	public void draw() {
		debug.drawDebugInfo();
		text.drawArtworkCaption(titleOfArtwork, artistFullName, additionalCredits, captionTextColor, captionBackgroundColor);
	}
	
	// Called when a key event occurs in the parent applet. 
	// Drawing is allowed because key events are queued, unless the sketch has called noLoop().
	public void keyEvent(KeyEvent e) {
		switch (e.getAction()) {
			case KeyEvent.RELEASE:
				debug.toggleOn();
				break;
		}
	}
	
	// Method called after draw has completed and the frame is done. No drawing allowed.
	public void post() {
		screenCapture.checkSave();
	}
	
	// Anything in here will be called automatically when 
	// the parent sketch shuts down.
	public void dispose() {
		computerVision.dispose();
		cam.stop();
		cam = null;
	}
	
	public float cameraXToScreen(float x, float srcWidth) {
		return constrain((float) x * (float) p.width / srcWidth, 0, p.width);
	}
	
	public float cameraYToScreen(float y, float srcHeight) {
		return constrain((float) y * (float) p.height / srcHeight, 0, p.height);
	}
	
	public PVector toScreenCoordinates(PVector pv, int srcWidth, int srcHeight) {
		float newX = constrain(round((float) pv.x * (float) p.width / (float) (srcWidth)), 0, p.width);
		float newY = constrain(round((float) pv.y * (float) p.height / (float) (srcHeight)), 0, p.height);
		
		return new PVector(newX, newY);
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
	
	/**
	 * return the version of the Library.
	 * 
	 * @return String
	 */
	public static String version() {
		return VERSION;
	}
}
