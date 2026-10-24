package com.shk.generic.docs;

/** Unknown slug or asset. Deliberately carries no request input, so nothing user-supplied is echoed back. */
public class DocNotFoundException extends RuntimeException {

    public DocNotFoundException() {
        super("No documentation page or image with that name");
    }
}
