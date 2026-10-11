import SwiftUI
import UIKit

/// Re-encodes a picked photo as a JPEG whose longest side is at most [maxSide] pixels, before it is
/// uploaded -- the iOS side of Android's `PhotoCropSheet.encodeCrop` (docs/PHASE17.md).
///
/// The picker hands over the original file: often a multi-MB HEIC, which Chrome can't show on the
/// web viewer, and which carries EXIF data such as the GPS location it was taken at. Drawing it
/// through `UIGraphicsImageRenderer` applies the EXIF orientation, drops that metadata, and keeps
/// list rows from downloading megabytes. A smaller image is never upscaled.
///
/// Returns `nil` if [data] isn't a decodable image.
func downscaledJpeg(_ data: Data, maxSide: CGFloat, quality: CGFloat = 0.88) -> Data? {
    guard let image = UIImage(data: data) else { return nil }
    let longest = max(image.size.width, image.size.height)
    let scale = longest > maxSide ? maxSide / longest : 1
    let target = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
        image.draw(in: CGRect(origin: .zero, size: target))
    }
    return resized.jpegData(compressionQuality: quality)
}

/// Photos and logos: 1024px, same as Android. Banners: 1280px wide (Android crops to 1280x720).
enum UploadImageSize {
    static let photo: CGFloat = 1024
    static let banner: CGFloat = 1280
}

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
