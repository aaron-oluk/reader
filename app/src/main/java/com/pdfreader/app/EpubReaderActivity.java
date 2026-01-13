package com.pdfreader.app;

import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class EpubReaderActivity extends AppCompatActivity {

    private WebView webView;
    private String epubPath;
    private String epubTitle;
    private File extractDir;
    private List<String> chapters;
    private int currentChapter = 0;
    private ReadingProgressManager progressManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_epub_reader);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        progressManager = new ReadingProgressManager(this);
        chapters = new ArrayList<>();

        epubPath = getIntent().getStringExtra("EPUB_PATH");
        epubTitle = getIntent().getStringExtra("EPUB_TITLE");

        if (epubTitle != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(epubTitle);
        }

        if (epubPath != null && !epubPath.isEmpty()) {
            loadEpub();
        } else {
            Toast.makeText(this, "Error: EPUB path not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadEpub() {
        try {
            // Create extraction directory
            extractDir = new File(getCacheDir(), "epub_" + System.currentTimeMillis());
            extractDir.mkdirs();

            // Extract EPUB (which is a ZIP file)
            InputStream inputStream;
            if (epubPath.startsWith("content://")) {
                inputStream = getContentResolver().openInputStream(Uri.parse(epubPath));
            } else {
                inputStream = new java.io.FileInputStream(epubPath);
            }

            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                File file = new File(extractDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(file);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zipInputStream.closeEntry();
            }
            zipInputStream.close();
            inputStream.close();

            // Parse content.opf to find reading order
            parseEpubStructure();

            // Load saved progress
            currentChapter = progressManager.getProgress(epubPath);
            if (currentChapter >= chapters.size()) currentChapter = 0;

            // Display first/saved chapter
            if (!chapters.isEmpty()) {
                displayChapter(currentChapter);
            } else {
                Toast.makeText(this, "No content found in EPUB", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error loading EPUB: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void parseEpubStructure() {
        try {
            // Find container.xml to locate content.opf
            File containerFile = new File(extractDir, "META-INF/container.xml");
            if (!containerFile.exists()) {
                // Try to find any .opf file
                findOpfFile(extractDir);
                return;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document containerDoc = builder.parse(containerFile);

            NodeList rootfiles = containerDoc.getElementsByTagName("rootfile");
            if (rootfiles.getLength() > 0) {
                Element rootfile = (Element) rootfiles.item(0);
                String opfPath = rootfile.getAttribute("full-path");
                File opfFile = new File(extractDir, opfPath);
                parseOpf(opfFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: find HTML/XHTML files
            findHtmlFiles(extractDir);
        }
    }

    private void findOpfFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                findOpfFile(file);
            } else if (file.getName().endsWith(".opf")) {
                parseOpf(file);
                return;
            }
        }
    }

    private void parseOpf(File opfFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(opfFile);

            File opfDir = opfFile.getParentFile();

            // Try to get spine order
            NodeList spineItems = doc.getElementsByTagName("itemref");
            NodeList manifestItems = doc.getElementsByTagName("item");

            // Build manifest map
            java.util.Map<String, String> manifest = new java.util.HashMap<>();
            for (int i = 0; i < manifestItems.getLength(); i++) {
                Element item = (Element) manifestItems.item(i);
                manifest.put(item.getAttribute("id"), item.getAttribute("href"));
            }

            // Get spine order
            for (int i = 0; i < spineItems.getLength(); i++) {
                Element itemref = (Element) spineItems.item(i);
                String idref = itemref.getAttribute("idref");
                String href = manifest.get(idref);
                if (href != null) {
                    File chapterFile = new File(opfDir, href);
                    if (chapterFile.exists()) {
                        chapters.add(chapterFile.getAbsolutePath());
                    }
                }
            }

            if (chapters.isEmpty()) {
                // Fallback to manifest items
                for (int i = 0; i < manifestItems.getLength(); i++) {
                    Element item = (Element) manifestItems.item(i);
                    String mediaType = item.getAttribute("media-type");
                    if (mediaType.contains("html") || mediaType.contains("xhtml")) {
                        String href = item.getAttribute("href");
                        File chapterFile = new File(opfDir, href);
                        if (chapterFile.exists()) {
                            chapters.add(chapterFile.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            findHtmlFiles(extractDir);
        }
    }

    private void findHtmlFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                findHtmlFiles(file);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) {
                    chapters.add(file.getAbsolutePath());
                }
            }
        }
    }

    private void displayChapter(int index) {
        if (index < 0 || index >= chapters.size()) return;

        try {
            File chapterFile = new File(chapters.get(index));
            StringBuilder content = new StringBuilder();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(chapterFile), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            // Inject CSS for better reading
            String html = content.toString();
            String css = "<style>" +
                    "body { font-family: serif; font-size: 18px; line-height: 1.6; padding: 16px; margin: 0; }" +
                    "img { max-width: 100%; height: auto; }" +
                    "</style>";

            if (!html.contains("<head>")) {
                html = css + html;
            } else {
                html = html.replace("<head>", "<head>" + css);
            }

            webView.loadDataWithBaseURL(
                    "file://" + chapterFile.getParent() + "/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
            );

            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle("Chapter " + (index + 1) + " of " + chapters.size());
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error displaying chapter", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save reading progress
        if (epubPath != null) {
            progressManager.saveProgress(epubPath, currentChapter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up extracted files
        if (extractDir != null && extractDir.exists()) {
            deleteRecursive(extractDir);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
