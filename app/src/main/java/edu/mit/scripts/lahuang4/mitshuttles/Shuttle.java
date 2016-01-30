package edu.mit.scripts.lahuang4.mitshuttles;

/**
 * Created by Lauren on 1/29/2016.
 */
public class Shuttle {

    String code, name;
    String[] stopCodes, stopNames;

    public Shuttle(String code, String name, String[] stopCodes, String[] stopNames) {
        this.code = code;
        this.name = name;
        this.stopCodes = stopCodes;
        this.stopNames = stopNames;
    }

}
