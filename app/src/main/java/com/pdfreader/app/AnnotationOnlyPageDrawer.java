package com.pdfreader.app;

import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.rendering.PageDrawer;
import com.tom_roush.pdfbox.rendering.PageDrawerParameters;

import java.io.IOException;

/**
 * Skips drawing the page's own content stream, so rendering only produces the
 * annotation/widget appearance layer. Used to overlay annotations on top of a page
 * that was already rendered by another engine.
 */
public class AnnotationOnlyPageDrawer extends PageDrawer {

    public AnnotationOnlyPageDrawer(PageDrawerParameters parameters) throws IOException {
        super(parameters);
    }

    @Override
    public void processPage(PDPage page) throws IOException {
        // Intentionally does not call super.processPage(page): that would draw the page's
        // content stream, which we don't want here (see class comment).
    }
}
