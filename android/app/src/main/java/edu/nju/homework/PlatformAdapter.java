package edu.nju.homework;

/** A platform owns its authentication entry, expected origin and reader contract. */
public interface PlatformAdapter {
 String id();
 String entryUrl();
 String expectedHost();
 String readerAsset();
 boolean available();
}
