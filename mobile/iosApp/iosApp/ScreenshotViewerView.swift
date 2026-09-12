import SwiftUI

/// Full-screen, pinch-zoomable view of a payment-proof screenshot -- iOS equivalent of
/// `androidApp/.../ui/ScreenshotViewerScreen.kt`. `AsyncImage` (native SwiftUI, no library needed)
/// handles the remote-JPEG load; `MagnificationGesture` is the SwiftUI equivalent of Android's
/// pinch/pan Compose recipe. **Authored but unverified, not wired into any navigation.**
struct ScreenshotViewerView: View {
    let imageUrl: String

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1

    var body: some View {
        AsyncImage(url: URL(string: imageUrl)) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .scaledToFit()
                    .scaleEffect(scale)
                    .gesture(
                        MagnificationGesture()
                            .onChanged { value in
                                scale = min(max(lastScale * value, 1), 5)
                            }
                            .onEnded { _ in
                                lastScale = scale
                            }
                    )
            case .failure:
                Text("Couldn't load this screenshot.").foregroundColor(.red)
            default:
                ProgressView()
            }
        }
    }
}
