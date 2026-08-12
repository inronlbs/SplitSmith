## What's Changed in v0.3.9.1
* **Google Pay Shared URI Caching**: Fixed GPay payment slip imports getting stuck on import screen by synchronously copying transient content:// image URIs into cacheDir.
* **Uniform Image Viewer**: Integrated a reusable, full-screen zoomable and pan-enabled AttachmentImageViewerDialog for previewing payment proofs and receipts.
* **Settlement Proof Polish**: Added a minimal styled camera button, local confirmation image preview, and delete button for debtors.
* **Double Settle Protection**: Blocked duplicate settlements by disabling the individual Settle row buttons and labeling them Pending while requests are awaiting approval.
