import SwiftUI

/// Full-screen, pinch-zoomable view of a payment-proof screenshot -- iOS equivalent of
/// `androidApp/.../ui/ScreenshotViewerScreen.kt` (design screen H). `AsyncImage` (native SwiftUI,
/// no library needed) handles the remote-JPEG load; `MagnificationGesture` is the SwiftUI
/// equivalent of Android's pinch/pan Compose recipe. The Android zoom chip and minimap (H2) are not
/// mirrored here. **Authored but unverified, not wired into any navigation.**
struct ScreenshotViewerView: View {
    let imageUrl: String
    var onBack: () -> Void = {}

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var attempt = 0
    /// U4 E1: the bar fades appear on the first zoom and then stay (invisible over the 1x black letterbox).
    @State private var everZoomed = false

    /// Same independent https-only check as Android: a `file://` value must never read local storage.
    private var url: URL? {
        imageUrl.hasPrefix("https://") ? URL(string: imageUrl) : nil
    }

    /// U4 E1: a dark fade behind the status bar and the home indicator so their light icons stay readable
    /// on a white receipt -- safe-area inset + 24 pt, stops .60 / .35 / 0 (the mid stop removes the band
    /// a straight ramp leaves on white). Fades in over 150 ms on the first zoom.
    private var barFades: some View {
        GeometryReader { proxy in
            let stops = [
                Gradient.Stop(color: .black.opacity(0.6), location: 0),
                Gradient.Stop(color: .black.opacity(0.35), location: 0.5),
                Gradient.Stop(color: .black.opacity(0), location: 1),
            ]
            VStack(spacing: 0) {
                LinearGradient(stops: stops, startPoint: .top, endPoint: .bottom)
                    .frame(height: proxy.safeAreaInsets.top + 24)
                Spacer(minLength: 0)
                LinearGradient(stops: stops, startPoint: .bottom, endPoint: .top)
                    .frame(height: proxy.safeAreaInsets.bottom + 24)
            }
            .ignoresSafeArea()
        }
        .allowsHitTesting(false)
        .opacity(everZoomed ? 1 : 0)
        .animation(.easeOut(duration: 0.15), value: everZoomed)
    }

    var body: some View {
        ZStack {
            Color(red: 5 / 255, green: 8 / 255, blue: 5 / 255).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack(spacing: 4) {
                    Button(action: onBack) { Image(systemName: "arrow.left").foregroundColor(.white).frame(width: 48, height: 40) }
                    Text("Payment screenshot").font(.system(size: 15, weight: .semibold)).foregroundColor(.white)
                    Spacer()
                }
                .padding(.leading, 6)
                .opacity(scale > 1.01 ? 0 : 1)

                AsyncImage(url: url, transaction: Transaction(animation: nil)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            .scaleEffect(scale)
                            .gesture(
                                MagnificationGesture()
                                    .onChanged { value in scale = min(max(lastScale * value, 1), 5) }
                                    .onEnded { _ in lastScale = scale }
                            )
                    case .failure:
                        VStack(spacing: 10) {
                            Image(systemName: "photo").font(.system(size: 36)).foregroundColor(Color(red: 94 / 255, green: 112 / 255, blue: 100 / 255))
                            Text("Couldn't load this screenshot.").font(.system(size: 17, weight: .bold)).foregroundColor(.white)
                            Text("Check your connection and try again.").font(.system(size: 13)).foregroundColor(Color(red: 143 / 255, green: 160 / 255, blue: 148 / 255))
                            Button("Retry") { attempt += 1 }
                                .font(.system(size: 13.5, weight: .semibold))
                                .foregroundColor(Color(red: 11 / 255, green: 20 / 255, blue: 13 / 255))
                                .padding(.horizontal, 20)
                                .frame(height: 40)
                                .background(Capsule().fill(Color(red: 242 / 255, green: 181 / 255, blue: 68 / 255)))
                        }
                    default:
                        ProgressView().tint(.white)
                    }
                }
                .id(attempt)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(EdgeInsets(top: 29, leading: 28, bottom: 35, trailing: 28))

                Button(action: onBack) {
                    Text("Back").font(.system(size: 14.5, weight: .semibold)).foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .overlay(Capsule().stroke(Color.white.opacity(0.3), lineWidth: 2))
                }
                .padding(EdgeInsets(top: 0, leading: 18, bottom: 28, trailing: 18))
                .opacity(scale > 1.01 ? 0 : 1)
            }
            barFades
        }
        .onChange(of: scale) { _, newScale in if newScale > 1.01 { everZoomed = true } }
        // .lightContent: white status-bar icons over the dark fades (U4 E1).
        .preferredColorScheme(.dark)
    }
}
