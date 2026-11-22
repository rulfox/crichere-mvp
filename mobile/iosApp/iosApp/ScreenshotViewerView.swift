import SwiftUI

/// Design screen H (androidApp `ui/ScreenshotViewerScreen.kt`): full-screen, pinch-zoomable view of a
/// payment-proof screenshot or a profile photo (H1). Zooming in hides the top bar and Back for a zoom
/// chip and a minimap of the visible area (H2); a failed load offers Retry (H3).
struct ScreenshotViewerView: View {
    let imageUrl: String
    var title: String = "Payment screenshot"
    var onBack: () -> Void = {}

    @State private var image: UIImage?
    @State private var failed = false
    @State private var attempt = 0

    var body: some View {
        ScreenshotViewerContent(image: image, failed: failed, title: title, onRetry: { attempt += 1 }, onBack: onBack)
            .crichereScreen()
            .lightStatusBarIcons(true)
            .task(id: attempt) { await load() }
    }

    /// The backend already restricts this field to https://, but this is a second, independent check
    /// right where the URL is opened: a `file://` value must never read local storage.
    private func load() async {
        failed = false
        guard imageUrl.hasPrefix("https://"), let url = URL(string: imageUrl) else { failed = true; return }
        do {
            // A failed load isn't cached, so Retry asks again.
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let decoded = UIImage(data: data) else { failed = true; return }
            image = decoded
        } catch {
            if !Task.isCancelled { failed = true }
        }
    }
}

private let viewerBg = Color(hex: 0x050805)
private let viewerIconMuted = Color(hex: 0x5E7064)
private let maxZoom: CGFloat = 5

/// The screen, stateless apart from the zoom, for the DEBUG parity gallery ([initialScale] /
/// [initialOffset] draw H2).
struct ScreenshotViewerContent: View {
    let image: UIImage?
    let failed: Bool
    var title: String = "Payment screenshot"
    var onRetry: () -> Void = {}
    var onBack: () -> Void = {}

    @State private var scale: CGFloat
    @State private var offset: CGSize
    @State private var baseScale: CGFloat
    @State private var baseOffset: CGSize
    @State private var imageBounds: CGRect = .zero
    @State private var everZoomed: Bool

    init(image: UIImage?, failed: Bool, title: String = "Payment screenshot", onRetry: @escaping () -> Void = {},
         onBack: @escaping () -> Void = {}, initialScale: CGFloat = 1, initialOffset: CGSize = .zero) {
        self.image = image
        self.failed = failed
        self.title = title
        self.onRetry = onRetry
        self.onBack = onBack
        _scale = State(initialValue: initialScale)
        _offset = State(initialValue: initialOffset)
        _baseScale = State(initialValue: initialScale)
        _baseOffset = State(initialValue: initialOffset)
        _everZoomed = State(initialValue: initialScale > 1.01)
    }

    private var loaded: Bool { image != nil && !failed }
    private var zoomed: Bool { loaded && scale > 1.01 }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            ZStack {
                (failed ? CrichereColor.auctionBg : viewerBg)
                VStack(spacing: 0) {
                    ViewerTopBar(title: title, onBack: onBack)
                        .padding(.top, insets.top)
                        .opacity(zoomed ? 0 : 1)
                        .allowsHitTesting(!zoomed)
                    picture(screen: screen)
                        .padding(EdgeInsets(top: 29, leading: 28, bottom: 35, trailing: 28))
                    BackPill(onBack: onBack)
                        .padding(.horizontal, 18)
                        .padding(.bottom, 28 + insets.bottom)
                        .opacity(zoomed ? 0 : 1)
                        .allowsHitTesting(!zoomed)
                }
                if failed {
                    LoadFailed(onRetry: onRetry)
                } else if image == nil {
                    CrichereSpinner(color: .white, lineWidth: 4, size: 40)
                }
                BarFades(top: insets.top, bottom: insets.bottom).opacity(everZoomed ? 1 : 0).animation(.easeOut(duration: 0.15), value: everZoomed)
                if zoomed, let image { zoomOverlays(image, insets: insets, screen: screen) }
            }
            .coordinateSpace(name: "viewer")
            .contentShape(Rectangle())
            .gesture(transform(screen: screen), including: loaded ? .all : .subviews)
            .ignoresSafeArea()
        }
        .onChange(of: zoomed) { _, isZoomed in if isZoomed { everZoomed = true } }
    }

    /// ContentScale.Fit by hand, so the 6 pt corners sit on the image itself.
    @ViewBuilder private func picture(screen: CGSize) -> some View {
        GeometryReader { box in
            if let image, !failed {
                let ratio = image.size.height > 0 ? image.size.width / image.size.height : box.size.width / max(box.size.height, 1)
                let fit = ratio > box.size.width / max(box.size.height, 1)
                    ? CGSize(width: box.size.width, height: box.size.width / ratio)
                    : CGSize(width: box.size.height * ratio, height: box.size.height)
                // The unscaled image rect (the container isn't transformed, unlike the image itself).
                let container = box.frame(in: .named("viewer"))
                let bounds = CGRect(x: container.midX - fit.width / 2, y: container.midY - fit.height / 2, width: fit.width, height: fit.height)
                Image(uiImage: image)
                    .resizable()
                    .frame(width: fit.width, height: fit.height)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                    .scaleEffect(scale)
                    .offset(clampPan(offset, scale: scale, image: imageBounds, screen: screen))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel(title)
                    .onAppear { imageBounds = bounds }
                    .onChange(of: bounds) { _, rect in imageBounds = rect }
            }
        }
    }

    private func transform(screen: CGSize) -> some Gesture {
        let magnify = MagnifyGesture()
            .onChanged { value in
                scale = min(max(baseScale * value.magnification, 1), maxZoom)
                offset = clampPan(offset, scale: scale, image: imageBounds, screen: screen)
            }
            .onEnded { _ in baseScale = scale; baseOffset = offset }
        let drag = DragGesture(minimumDistance: 1)
            .onChanged { value in
                let moved = CGSize(width: baseOffset.width + value.translation.width, height: baseOffset.height + value.translation.height)
                offset = clampPan(moved, scale: scale, image: imageBounds, screen: screen)
            }
            .onEnded { _ in baseOffset = offset }
        return magnify.simultaneously(with: drag)
    }

    @ViewBuilder private func zoomOverlays(_ image: UIImage, insets: EdgeInsets, screen: CGSize) -> some View {
        Text(String(format: "%.1f×", scale))
            .crichere(.mono(12, .semiBold, lineHeight: 12), .white)
            .padding(.horizontal, 12)
            .frame(height: 30)
            .background(Color.black.opacity(0.65), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
            .padding(.leading, 18)
            .padding(.top, insets.top + 6)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .allowsHitTesting(false)
        Minimap(image: image, visible: visibleFraction(scale: scale, offset: clampPan(offset, scale: scale, image: imageBounds, screen: screen), image: imageBounds, screen: screen))
            .padding(.trailing, 18)
            .padding(.bottom, insets.bottom + 32)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .allowsHitTesting(false)
    }
}

private struct ViewerTopBar: View {
    let title: String
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onBack) {
                CrIcon("ic_arrow_back", size: 24, tint: .white).frame(width: 48, height: 48)
            }
            .buttonStyle(.pressCircle)
            .accessibilityLabel("Back")
            Text(title).crichere(.sans(15, .semiBold, lineHeight: 15), .white)
            Spacer(minLength: 0)
        }
        .padding(.leading, 6)
        .frame(height: 40)
    }
}

private struct BackPill: View {
    let onBack: () -> Void

    var body: some View {
        Button(action: onBack) {
            Text("Back").crichere(.sans(14.5, .semiBold, lineHeight: 14.5)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
        }
        .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: .white.opacity(0.1), content: .white, border: .white.opacity(0.3), radius: 24))
    }
}

/// H3: the screenshot didn't load.
private struct LoadFailed: View {
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            CrIcon("ic_broken_image", size: 40, tint: viewerIconMuted)
            Spacer().frame(height: 14)
            Text("Couldn't load this screenshot.").crichere(.archivo(17, .bold, lineHeight: 20.4), .white)
            Spacer().frame(height: 10)
            Text("Check your connection and try again.").crichere(.sans(13, lineHeight: 18.85), CrichereColor.auctionMuted)
            Spacer().frame(height: 14)
            Button(action: onRetry) {
                Text("Retry").crichere(.sans(13.5, .semiBold)).padding(.horizontal, 20).frame(height: 40)
            }
            .buttonStyle(PillButtonStyle(fill: CrichereColor.auctionGold, pressedFill: CrichereColor.auctionGold.opacity(0.85),
                                         content: CrichereColor.auctionBg, radius: 20))
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 40)
    }
}

/// U4 E1: a dark fade behind the status bar and the home indicator so their light icons stay readable
/// on a white receipt -- inset + 24 pt, stops .60 / .35 / 0 (the mid stop removes the band a straight
/// ramp leaves on white). Shown from the first zoom on; invisible over the 1x black letterbox.
private struct BarFades: View {
    let top: CGFloat
    let bottom: CGFloat

    var body: some View {
        let stops = [
            Gradient.Stop(color: .black.opacity(0.6), location: 0),
            Gradient.Stop(color: .black.opacity(0.35), location: 0.5),
            Gradient.Stop(color: .black.opacity(0), location: 1),
        ]
        VStack(spacing: 0) {
            LinearGradient(stops: stops, startPoint: .top, endPoint: .bottom).frame(height: top + 24)
            Spacer(minLength: 0)
            LinearGradient(stops: stops, startPoint: .bottom, endPoint: .top).frame(height: bottom + 24)
        }
        .allowsHitTesting(false)
    }
}

/// H2: the whole screenshot in miniature, with the part on screen outlined in gold.
private struct Minimap: View {
    let image: UIImage
    let visible: CGRect

    var body: some View {
        let ratio = image.size.height > 0 ? image.size.width / image.size.height : 56.0 / 102.0
        let height = min(max(56 / ratio, 32), 120)
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        ZStack(alignment: .topLeading) {
            Image(uiImage: image).resizable().frame(width: 56, height: height).opacity(0.7)
            Rectangle()
                .strokeBorder(CrichereColor.auctionGold, lineWidth: 2)
                .frame(width: max(visible.width * 56, 2), height: max(visible.height * height, 2))
                .offset(x: visible.minX * 56, y: visible.minY * height)
        }
        .frame(width: 56, height: height, alignment: .topLeading)
        .padding(2)
        .background(Color.black, in: shape)
        .overlay(shape.strokeBorder(.white.opacity(0.5), lineWidth: 2))
        .clipShape(shape)
    }
}

/// Keeps a zoomed image covering the screen: it can pan until its edge meets the screen edge, and
/// stays centred on an axis where it is still narrower than the screen.
private func clampPan(_ offset: CGSize, scale: CGFloat, image: CGRect, screen: CGSize) -> CGSize {
    guard !image.isEmpty, screen.width > 0 else { return .zero }
    func axis(_ value: CGFloat, _ length: CGFloat, _ center: CGFloat, _ screenLength: CGFloat) -> CGFloat {
        let scaled = length * scale
        if scaled <= screenLength { return 0 }
        return min(max(center + value, screenLength - scaled / 2), scaled / 2) - center
    }
    return CGSize(width: axis(offset.width, image.width, image.midX, screen.width),
                  height: axis(offset.height, image.height, image.midY, screen.height))
}

/// The part of the image on screen, as fractions of the image (0...1 on each axis).
private func visibleFraction(scale: CGFloat, offset: CGSize, image: CGRect, screen: CGSize) -> CGRect {
    guard !image.isEmpty else { return CGRect(x: 0, y: 0, width: 1, height: 1) }
    let halfW = image.width * scale / 2
    let halfH = image.height * scale / 2
    let shown = CGRect(x: image.midX + offset.width - halfW, y: image.midY + offset.height - halfH, width: halfW * 2, height: halfH * 2)
    let onScreen = shown.intersection(CGRect(origin: .zero, size: screen))
    guard !onScreen.isNull else { return .zero }
    func clamp(_ v: CGFloat) -> CGFloat { min(max(v, 0), 1) }
    let left = clamp((onScreen.minX - shown.minX) / shown.width)
    let top = clamp((onScreen.minY - shown.minY) / shown.height)
    let right = clamp((onScreen.maxX - shown.minX) / shown.width)
    let bottom = clamp((onScreen.maxY - shown.minY) / shown.height)
    return CGRect(x: left, y: top, width: right - left, height: bottom - top)
}
