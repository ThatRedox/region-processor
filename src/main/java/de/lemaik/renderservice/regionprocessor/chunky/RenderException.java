package de.lemaik.renderservice.regionprocessor.chunky;

public class RenderException extends Exception {

  public RenderException(String message) {
    super(message);
  }

  public RenderException(String message, Exception inner) {
    super(message, inner);
  }
}
