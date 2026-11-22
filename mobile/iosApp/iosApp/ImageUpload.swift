import SwiftUI
import UIKit

/// `AsyncImage` loads through `URLSession.shared`, so `URLCache.shared` is its disk cache. The
/// default is small; uploads are served `immutable` with a fresh `?v=` per upload, so a larger
/// cache keeps roster photos across launches without ever going stale (docs/PHASE17.md).
func configureImageCache() {
    URLCache.shared = URLCache(memoryCapacity: 32 * 1024 * 1024, diskCapacity: 200 * 1024 * 1024)
}

/// A list-row image (player photo, franchise logo) that fills its frame, cropped. Draws nothing
/// while loading, on failure, or when [url] is `nil`, so the initials underneath show through.
struct RemoteImage: View {
    let url: String?

    var body: some View {
        if let url, let parsed = URL(string: url) {
            AsyncImage(url: parsed) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                }
            }
        }
    }
}
