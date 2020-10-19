package de.lemaik.renderservice.regionprocessor.chunky;

public class BinarySceneData {

  private final byte[] octree;
  private final byte[] emittergrid;

  public BinarySceneData(byte[] octree, byte[] emittergrid) {
    this.octree = octree;
    this.emittergrid = emittergrid;
  }

  public byte[] getOctree() {
    return octree;
  }

  public byte[] getEmittergrid() {
    return emittergrid;
  }
}
