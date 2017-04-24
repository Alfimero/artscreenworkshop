/*
 * Motion Triangles
 * by Andrew Ringler
 * 
 * Credits:
 * None
 */
import artscreen.*;
import processing.video.*;
import largesketchviewer.*;
import gab.opencv.*;

ArtScreen artScreen;

void setup() {
  size(1920, 1080, P3D);
  artScreen = new ArtScreen(this, "“Motion Triangles”, 2017", "by Andrew Ringler", "", color(255), color(0), 100);
}

void draw() {
  // replace background with transparency, to keep faded trails
  fill(0, 0, 0, 15);
  rect(0, 0, width, height);
  
  // iterate over motion pixels, grab at most 10
  // or less if less are available
  fill(217, 30, 197, 100);
  noStroke();
  for (int i=0; i<100 && i<artScreen.top100MotionPixels.length; i++) {
    MotionPixel motionPixel = artScreen.top100MotionPixels[i];
    triangle(
      motionPixel.location.x, motionPixel.location.y, // point-1 
      motionPixel.location.x + motionPixel.changeAmount /2.0, motionPixel.location.y + motionPixel.changeAmount, // point-2
      motionPixel.location.x + motionPixel.changeAmount, motionPixel.location.y // point-3 
      );
  }
}