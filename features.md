# App Features

## 1. PDF Reader

Read PDF files with a smooth vertical-scroll page list. The reader lazy-loads pages for memory efficiency, tracks your current position automatically, and resumes where you left off the next time you open the same file.

**Sub-features**

- Auto-saves reading progress on pause/exit
- Page indicator that fades after a moment
- Share PDF to other apps directly from the reader

---

## 2. EPUB Reader

Open and read EPUB e-books. The app extracts the EPUB archive, parses chapter order from the content manifest, and renders each chapter in a web view with clean, consistent typography (serif font, comfortable line spacing, responsive images).

**Sub-features**

- Chapter navigation with "Chapter X of Y" counter
- Remembers last read chapter across sessions
- Automatically cleans up extracted files on exit

---

## 3. Library

Browse your entire book collection in a responsive grid. The grid adapts its column count to the screen width (2 – 4 columns). Each card shows a generated cover thumbnail and a reading-progress bar.

**Sub-features**

- Add PDFs or EPUBs from device storage
- Real-time search by book title
- Long-press a book to remove it from the library
- Filter tabs: All, Currently Reading, To Read, Finished

---

## 4. File Search

Scan common device directories (Downloads, Documents, DCIM, root storage) recursively up to five levels deep to find all PDF and EPUB files. Results update instantly as you type in the search bar.

---

## 5. Home Dashboard

A personalised home screen that greets you by time of day, shows your currently-reading book with a progress bar, and lists your five most recent books.

**Sub-features**

- Continue reading with one tap
- Quick-action buttons for Open, Scan, History, and Browse
- Long-press a book for a context menu (remove, share)

---

## 6. Notes

Add free-text notes to any page while reading a PDF. Tap a page to view or edit existing notes. Notes are stored per file and persist across sessions.

**Sub-features**

- Create, edit, and delete notes per page
- Notes icon in the toolbar for quick access

---

## 7. Highlights

Long-press anywhere on a PDF page to create a highlight with an optional note attached. Tap an existing highlight to view, edit, or delete it.

---

## 8. Bookmarks

Bookmark any page while reading. Optionally add a label to each bookmark. Toggle the bookmark on/off; an automatic "Last read" bookmark is always kept up to date.

**Sub-features**

- View all bookmarks for a file in one list
- Jump to any bookmarked page instantly

---

## 9. Sign PDF

Add a digital signature to any PDF. Tap anywhere on a page to place the signature at that exact spot. The signature renders with a transparent background so it sits cleanly on top of the document content.

**Sub-features**

- Draggable: move the signature anywhere on the page after placing it
- Resizable: drag the three corner handles (top-left, bottom-left, bottom-right) to scale freely in both axes
- Deletable: tap the red ✕ handle (top-right corner) to remove the signature from a page
- Scrolling outside the signature area works normally; only touches inside the signature interact with it
- Signed PDF is saved to a dedicated Signed folder

---

## 10. Draw Signature

Draw your signature directly on screen with a touch canvas. The canvas provides a white drawing surface while the exported bitmap is fully transparent so it blends onto documents without a white box behind it.

**Sub-features**

- Clear and redraw at any time
- Save with a custom name for reuse later

---

## 11. Capture Signature

Photograph a physical handwritten signature using the device camera and convert it into a digital signature.

**Sub-features**

- Flash toggle for low-light environments
- Crop tool with draggable corner handles to isolate just the signature
- Review screen with side-by-side before/after comparison
- Sensitivity slider (0 – 100) to control how aggressively the white background is removed
- Zoom controls (1× – 5×) on the review screen

---

## 12. Saved Signatures

Store as many signatures as you like and reuse them across documents. Access the full list from the Profile tab or during the signing workflow.

**Sub-features**

- Preview each saved signature
- Delete individual signatures
- All saved signatures are automatically loaded with transparent backgrounds

---

## 13. Scan Document

Use the camera to capture document pages and convert them into a single PDF.

**Sub-features**

- Real-time camera preview
- Flash toggle
- Capture multiple pages in sequence
- Image counter showing pages captured so far
- Name the output PDF before saving

---

## 14. Merge PDFs

Select two or more PDF files and combine them into a single document. A progress bar tracks the merge operation, and the result is saved to a dedicated Merged folder.

---

## 15. Image to PDF

Select one or more images from device storage and convert them into a PDF, one image per page. Each image is scaled to fit the page while preserving its aspect ratio. The result is saved to a dedicated Converted folder.

---

## 16. Reading Insights

View statistics about your reading habits on the Insights tab.

**Sub-features**

- Monthly pages read
- Books finished count
- Estimated reading speed (words per minute)
- Current daily reading streak
- Yearly reading goal with progress bar
- Monthly page breakdown
- Weekly activity with percentage change vs the prior week

---

## 17. Profile

See a summary of your library and reading activity in one place, and access signature management.

**Sub-features**

- Total books, total pages read, current streak
- Daily reading goal (minutes and pages)
- Quick link to Signature Management

---

## 18. File Management

All files created by the app (signed PDFs, merged PDFs, conversions, scans) are organised into named subfolders inside a PDFReader directory. On Android 10 and above the files are saved to the public Downloads folder via the MediaStore API so they are immediately visible in the system file manager.

**Folder structure**

```
PDFReader/
├── Signed/
├── Merged/
├── Converted/
└── Scanned/
```
