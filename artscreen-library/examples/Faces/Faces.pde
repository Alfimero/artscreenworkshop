/*
 * Faces
 * by Andrew Ringler
 * 
 */
import artscreen.*;
ArtScreen artScreen;

void setup() {
  size(1920, 1080);
  artScreen = new ArtScreen(this, "Faces", "by Andrew", "", color(0, 0, 0), color(255, 255, 255));
}

void draw() {
  background(0);
  
  /* since we are rear-projecting our image will be
   * mirrored, so we want to flip it, so text reads correctly
   * and so that user's motion is as expected */
  scale(-1, 1);
  translate(-width, 0);
  
  /* Draw an ellipse every place we detect a face */
  noStroke();
  fill(255);
  for(Face face : artScreen.faces){
    ellipse(face.location.x, face.location.y, face.width, face.height);
  }
}