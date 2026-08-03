package com.pdfreader.app;

import com.tom_roush.pdfbox.contentstream.PDFStreamEngine;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.rendering.PageDrawer;
import com.tom_roush.pdfbox.rendering.PageDrawerParameters;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Skips drawing the page's own content stream, so rendering only produces the
 * annotation/widget appearance layer. Used to overlay annotations on top of a page
 * that was already rendered by another engine.
 *
 * PDFStreamEngine.processPage(page) normally does two things: initPage(page) (seeds the
 * graphics-state stack, currentPage, matrices) and then processStream(page) (walks the
 * content-stream operators and draws). Both are private, so there's no protected seam to
 * call just the init half. Skipping processPage entirely leaves the graphics stack empty,
 * which crashes with a NullPointerException the moment any annotation is drawn (showAnnotation
 * -> saveGraphicsStack() -> graphicsStack.peek().clone() on an empty stack). So this still runs
 * the real page-state setup via reflection into the private initPage method, then simply never
 * calls the (also private) processStream() that would draw the actual content.
 */
public class AnnotationOnlyPageDrawer extends PageDrawer {

    private static volatile Method initPageMethod;

    public AnnotationOnlyPageDrawer(PageDrawerParameters parameters) throws IOException {
        super(parameters);
    }

    @Override
    public void processPage(PDPage page) throws IOException {
        try {
            getInitPageMethod().invoke(this, page);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Could not initialize PdfBox-Android page state", e);
        }
        // Intentionally never calls the (private) processStream(page): that would draw
        // the page's actual content, which we don't want here (see class comment).
    }

    private static Method getInitPageMethod() throws ReflectiveOperationException {
        Method method = initPageMethod;
        if (method == null) {
            method = PDFStreamEngine.class.getDeclaredMethod("initPage", PDPage.class);
            method.setAccessible(true);
            initPageMethod = method;
        }
        return method;
    }
}
