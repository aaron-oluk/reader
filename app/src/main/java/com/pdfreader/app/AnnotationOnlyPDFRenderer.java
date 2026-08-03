package com.pdfreader.app;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.rendering.PageDrawer;
import com.tom_roush.pdfbox.rendering.PageDrawerParameters;

import java.io.IOException;

/**
 * Renders only the annotation/widget appearance layer of each page (see
 * AnnotationOnlyPageDrawer), producing a transparent image suitable for compositing
 * on top of a page rendered by another engine.
 */
public class AnnotationOnlyPDFRenderer extends PDFRenderer {

    public AnnotationOnlyPDFRenderer(PDDocument document) {
        super(document);
    }

    @Override
    protected PageDrawer createPageDrawer(PageDrawerParameters parameters) throws IOException {
        PageDrawer drawer = new AnnotationOnlyPageDrawer(parameters);
        // Propagate whatever filter is set via setAnnotationsFilter(...), matching
        // PDFRenderer's own default createPageDrawer() behavior.
        drawer.setAnnotationFilter(getAnnotationsFilter());
        return drawer;
    }
}
