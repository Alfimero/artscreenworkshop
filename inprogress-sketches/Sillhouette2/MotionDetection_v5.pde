/* ArtScreenCV (Art Screen Computer Vision)
 *
 * various functions to assist in analyzing the images
 * coming in from the webcam
 */
import java.util.PriorityQueue;

float MAX_PIXEL_CHANGE = 442; // sqrt(255^2 + 255^2 + 255^2) ~= 442
float MOTION_THRESHOLD = 80f;

boolean debug = false;
boolean movementDetected = false;
PVector maxMotionLocation = new PVector(0, 0);
MotionPixel[] top100MotionPixels = new MotionPixel[] {};

PImage previousProcessingFrame; // smaller frame for image processing / previous frame
PImage processingFrame; // smaller frame for image processing
PImage motionImage;
int lastProcessedFrameNumber = -1;

void performMotionDetection() {
  // initialize empty images for the first time
  if (previousProcessingFrame == null) {
    previousProcessingFrame = createImage(artScreen.captureWidth / 4, artScreen.captureHeight / 4, RGB);
    processingFrame = createImage(artScreen.captureWidth / 4, artScreen.captureHeight / 4, RGB);
    motionImage = createImage(artScreen.captureWidth / 4, artScreen.captureHeight / 4, ARGB);
  }

  // if we have not yet processed the current video frame, do so
  if (lastProcessedFrameNumber != artScreen.captureFrameNumber && artScreen.captureFrameNumber >= 0) {
    // save off previous frame
    previousProcessingFrame.copy(processingFrame, 0, 0, processingFrame.width, processingFrame.height, 0, 0, previousProcessingFrame.width, previousProcessingFrame.height);

    // copy capture frame over to current frame variable, and shrink down in size
    processingFrame.copy(artScreen.captureFrame, 0, 0, artScreen.captureFrame.width, artScreen.captureFrame.height, 0, 0, processingFrame.width, processingFrame.height);

    if (artScreen.captureFrameNumber > 0 /* we need at least two frames for frame differencing */) {
      detectMotion();
    }

    lastProcessedFrameNumber = artScreen.captureFrameNumber;
  }
}

void detectMotion() {
  pushStyle();
  colorMode(RGB, 255);

  processingFrame.loadPixels();
  previousProcessingFrame.loadPixels();
  motionImage.loadPixels();

  MotionPixels motionPixels = new MotionPixels();

  float maxChange = 0;
  boolean newMotion = false;
  int newX = 0;
  int newY = 0;
  for (int x = 0; x < previousProcessingFrame.width; x++) {
    for (int y = 0; y < previousProcessingFrame.height; y++) {
      int loc = x + y * previousProcessingFrame.width; //1D pixel location

      // pull out red, green, blue values using fast bit-wise operations, see https://processing.org/reference/blue_.html, etc…
      float oldR = previousProcessingFrame.pixels[loc] >> 16 & 0xFF;
      float oldG = previousProcessingFrame.pixels[loc] >> 8 & 0xFF;
      float oldB = previousProcessingFrame.pixels[loc] & 0xFF;
      float newR = processingFrame.pixels[loc] >> 16 & 0xFF;
      float newG = processingFrame.pixels[loc] >> 8 & 0xFF;
      float newB = processingFrame.pixels[loc] & 0xFF;

      float change = dist(oldR, oldG, oldB, newR, newG, newB);
      if (change > MOTION_THRESHOLD) {
        if (change > maxChange) {
          newMotion = true;
          newX = x;
          newY = y;
        }
        int changeInt = constrain(round(change / MAX_PIXEL_CHANGE * 255f), 0, 255);
        motionImage.pixels[loc] = color(changeInt);
        PVector newXYProcessingCoordinates = new PVector(x, y);
        motionPixels.add(new MotionPixel(toScreenCoordinates(newXYProcessingCoordinates, previousProcessingFrame.width, previousProcessingFrame.height), changeInt));
      } else {
        motionImage.pixels[loc] = color(0, 0, 0, 0); // transparent black
      }
    }
  }
  motionImage.updatePixels();

  top100MotionPixels = motionPixels.toArray();

  movementDetected = newMotion;
  PVector motionPixel = new PVector(newX, newY);
  maxMotionLocation = toScreenCoordinates(motionPixel, previousProcessingFrame.width, previousProcessingFrame.height);

  popStyle();
}

float toScreenX(float x, float srcMaxX) {
  return constrain((float) x * (float) width / srcMaxX, 0, width);
}

float toScreenY(float y, float srcMaxY) {
  return constrain((float) y * (float) height / srcMaxY, 0, height);
}

PVector toScreenCoordinates(PVector pv, int srcWidth, int srcHeight) {
  float newX = constrain(round((float) pv.x * (float) width / (float) (srcWidth)), 0, width);
  float newY = constrain(round((float) pv.y * (float) height / (float) (srcHeight)), 0, height);

  return new PVector(newX, newY);
}

class MotionPixel implements Comparable<MotionPixel> {
  PVector location;
  int changeAmount;

  MotionPixel(PVector location, int changeAmount) {
    this.location = location;
    this.changeAmount = changeAmount;
  }

  int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + changeAmount;
    result = prime * result + ((location == null) ? 0 : location.hashCode());
    return result;
  }

  boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    MotionPixel other = (MotionPixel) obj;
    if (changeAmount != other.changeAmount)
      return false;
    if (location == null) {
      if (other.location != null)
        return false;
    } else if (!location.equals(other.location))
      return false;
    return true;
  }

  int compareTo(MotionPixel o) {
    return Integer.valueOf(o.changeAmount).compareTo(changeAmount);
  }
}

class MotionPixels {
  PriorityQueue<MotionPixel> queue = new PriorityQueue<MotionPixel>();

  void add(MotionPixel motionPixel) {
    queue.add(motionPixel);
    if (queue.size() > 100) {
      queue.remove();
    }
  }

  MotionPixel[] toArray() {
    return queue.toArray(new MotionPixel[queue.size()]);
  }
}


public void drawDebugInfo() {
  pushStyle();

  pushMatrix();
  if (sketchRenderer() == P3D) {
    camera(); // https://github.com/processing/processing/issues/2128
  } else {
    resetMatrix();
  }

  colorMode(RGB, 255);

  scale(-1, 1);
  translate(-width, 0);
  strokeWeight(2);
  rectMode(CENTER);
  fill(0);
  stroke(255);

  // draw change amount at every motion pixel
  for (MotionPixel motionPixel : top100MotionPixels) {
    float motionPixelVizHeight = map(motionPixel.changeAmount, 0, 255, 10, 100);
    rect(motionPixel.location.x, motionPixel.location.y, 3, motionPixelVizHeight);
  }

  popMatrix();
  popStyle();
}