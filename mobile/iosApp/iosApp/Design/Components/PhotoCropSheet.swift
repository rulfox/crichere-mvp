import SwiftUI
import UIKit

// The iOS twin of androidApp `ui/PhotoCropSheet.kt`: design screen C3 (circle crop for profile photos),
// G2 (rounded square for franchise logos) and I2 (16:9 league banner). Same geometry, zoom limits and
// output sizes as Android.

/// A photo the user picked: upright (EXIF orientation applied), at most 2048 px on its longest side.
struct PickedPhoto: Identifiable {
    let id = UUID()
    let image: UIImage
    let fileName: String
    var pixelWidth: CGFloat { image.size.width * image.scale }
    var pixelHeight: CGFloat { image.size.height * image.scale }
}

/// The square region of the photo (in pixels) the window currently covers.
struct CropSquare { let left: CGFloat; let top: CGFloat; let side: CGFloat }

/// A 16:9 region of the photo (in pixels) -- the league banner crop (design I2).
struct CropBanner { let left: CGFloat; let top: CGFloat; let width: CGFloat; let height: CGFloat }

private let maxDecodeDimension: CGFloat = 2048
/// Photos and logos: 1024 px (docs/PHASE17.md).
private let outputSize: CGFloat = 1024
private let maxZoom: CGFloat = 5
private let cropCircle: CGFloat = 230
private let cropSquareSide: CGFloat = 220
private let bannerWindowWidth: CGFloat = 300
private let bannerWindowHeight: CGFloat = 169
private let bannerOutput = CGSize(width: 1280, height: 720)

/// Decodes picked image data upright, downsampled so its longest side is at most 2048 px.
func decodePickedPhoto(_ data: Data, fileName: String? = nil) -> PickedPhoto? {
    guard let source = UIImage(data: data) else { return nil }
    let longest = max(source.size.width, source.size.height)
    let scale = longest > maxDecodeDimension ? maxDecodeDimension / longest : 1
    let target = CGSize(width: (source.size.width * scale).rounded(), height: (source.size.height * scale).rounded())
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    let upright = UIGraphicsImageRenderer(size: target, format: format).image { _ in source.draw(in: CGRect(origin: .zero, size: target)) }
    return PickedPhoto(image: upright, fileName: fileName ?? "photo.jpg")
}

private func cropped(_ photo: PickedPhoto, _ rect: CGRect) -> CGImage? {
    guard let cg = photo.image.cgImage else { return nil }
    let bounds = CGRect(x: 0, y: 0, width: cg.width, height: cg.height)
    let clamped = rect.integral.intersection(bounds)
    return clamped.isEmpty ? nil : cg.cropping(to: clamped)
}

private func jpeg(_ image: CGImage, size: CGSize, quality: CGFloat) -> Data? {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    return UIGraphicsImageRenderer(size: size, format: format).image { _ in
        UIImage(cgImage: image).draw(in: CGRect(origin: .zero, size: size))
    }.jpegData(compressionQuality: quality)
}

/// Crops [square] out of the photo as a JPEG of at most 1024 px (a smaller crop is never upscaled).
func encodeCrop(_ photo: PickedPhoto, _ square: CropSquare) -> Data? {
    guard let image = cropped(photo, CGRect(x: square.left, y: square.top, width: square.side, height: square.side)) else { return nil }
    let side = min(CGFloat(image.width), outputSize)
    return jpeg(image, size: CGSize(width: side, height: side), quality: 0.88)
}

/// Crops [region] out of the photo as a 1280×720 JPEG.
func encodeBannerCrop(_ photo: PickedPhoto, _ region: CropBanner) -> Data? {
    guard let image = cropped(photo, CGRect(x: region.left, y: region.top, width: region.width, height: region.height)) else { return nil }
    return jpeg(image, size: bannerOutput, quality: 0.85)
}

/// Design C3 / G2 / I2. Drag or pinch to position; the use button hands back the crop. [square] is the
/// rounded-square logo window; a non-nil [onUseBanner] is the 16:9 banner window and receives its crop
/// instead of [onUsePhoto]. Present inside `crichereSheet`.
struct PhotoCropSheet: View {
    let photo: PickedPhoto
    let title: String
    let subtitle: String
    let onDismiss: () -> Void
    let onChooseAnother: () -> Void
    let onUsePhoto: (CropSquare) -> Void
    var square: Bool = false
    var useLabel: String = "Use photo"
    var onUseBanner: ((CropBanner) -> Void)? = nil

    @State private var zoom: CGFloat = 1
    @State private var pan: CGSize = .zero
    @State private var gestureZoom: CGFloat = 1
    @State private var gesturePan: CGSize = .zero

    private var banner: Bool { onUseBanner != nil }
    private var windowW: CGFloat { banner ? bannerWindowWidth : (square ? cropSquareSide : cropCircle) }
    private var windowH: CGFloat { banner ? bannerWindowHeight : windowW }
    private var windowRadius: CGFloat { banner ? 8 : 28 }
    private var baseScale: CGFloat { max(windowW / photo.pixelWidth, windowH / photo.pixelHeight) }
    private var liveZoom: CGFloat { min(max(zoom * gestureZoom, 1), maxZoom) }
    private var livePan: CGSize { clampPan(CGSize(width: pan.width + gesturePan.width, height: pan.height + gesturePan.height), liveZoom) }

    private func clampPan(_ p: CGSize, _ z: CGFloat) -> CGSize {
        let s = baseScale * z
        let maxX = (photo.pixelWidth * s - windowW) / 2
        let maxY = (photo.pixelHeight * s - windowH) / 2
        return CGSize(width: min(max(p.width, -maxX), maxX), height: min(max(p.height, -maxY), maxY))
    }

    private var region: CropBanner {
        let s = baseScale * liveZoom
        let w = windowW / s
        let h = windowH / s
        return CropBanner(left: photo.pixelWidth / 2 - livePan.width / s - w / 2, top: photo.pixelHeight / 2 - livePan.height / s - h / 2, width: w, height: h)
    }

    private var crop: CropSquare { CropSquare(left: region.left, top: region.top, side: region.width) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            stage.padding(.top, 12)
            preview.padding(.top, 14)
            buttons.padding(.top, (square || banner) ? 14 : 19)
        }
        .padding(.horizontal, 15)
        .padding(.bottom, 24)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 6) {
            Button(action: onDismiss) { CrIcon("ic_arrow_back", size: 22, tint: CrichereColor.onBackground) }
                .buttonStyle(.plain)
                .padding(.top, 4)
                .accessibilityLabel("Back")
            VStack(alignment: .leading, spacing: 4) {
                Text(title).crichere(.sans(17, .semiBold, lineHeight: 17), CrichereColor.onBackground)
                Text(subtitle).crichere(.sans(12, lineHeight: 12), CrichereColor.onSurfaceVariant)
            }
        }
        .padding(.top, 12)
    }

    private var stage: some View {
        GeometryReader { proxy in
            let s = baseScale * liveZoom
            let drawW = photo.pixelWidth * s
            let drawH = photo.pixelHeight * s
            let center = CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)
            let window = CGRect(x: center.x - windowW / 2, y: center.y - windowH / 2, width: windowW, height: windowH)
            ZStack {
                Image(uiImage: photo.image)
                    .resizable()
                    .frame(width: drawW, height: drawH)
                    .position(x: center.x + livePan.width, y: center.y + livePan.height)
                Path { path in
                    path.addRect(CGRect(origin: .zero, size: proxy.size))
                    if square || banner {
                        path.addRoundedRect(in: window, cornerSize: CGSize(width: windowRadius, height: windowRadius))
                    } else {
                        path.addEllipse(in: window)
                    }
                }
                .fill(Color(hex: 0x0A0E0A, opacity: 0x9E / 255.0), style: FillStyle(eoFill: true))
                Group {
                    if square || banner {
                        RoundedRectangle(cornerRadius: windowRadius).stroke(.white, lineWidth: 2)
                    } else {
                        Circle().stroke(.white, lineWidth: 2)
                    }
                }
                .frame(width: windowW, height: windowH)
                .position(center)
            }
        }
        .frame(height: banner ? 300 : 320)
        .background(Color(hex: 0x101410))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .contentShape(Rectangle())
        .gesture(
            SimultaneousGesture(
                MagnifyGesture().onChanged { gestureZoom = $0.magnification }.onEnded { _ in
                    let z = liveZoom
                    pan = clampPan(pan, z)
                    zoom = z
                    gestureZoom = 1
                },
                DragGesture(minimumDistance: 0).onChanged { gesturePan = $0.translation }.onEnded { _ in
                    pan = livePan
                    gesturePan = .zero
                }
            )
        )
        .accessibilityLabel("Crop area. Drag or pinch to position.")
    }

    @ViewBuilder private var preview: some View {
        if banner {
            HStack(spacing: 10) {
                regionImage(width: 96, height: 54, radius: 6)
                Text("Shown on the dashboard card and league page").crichere(.sans(12, lineHeight: 16.2), CrichereColor.onSurfaceVariant)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 9)
            .frame(height: 72)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
            .padding(.horizontal, 4)
        } else if square {
            HStack(spacing: 10) {
                regionImage(width: 32, height: 32, radius: 8)
                regionImage(width: 48, height: 48, radius: 12)
                Text("At auction and roster sizes").crichere(.sans(12, lineHeight: 16.2), CrichereColor.onSurfaceVariant)
            }
            .padding(.horizontal, 4)
        } else {
            HStack(spacing: 16) {
                regionImage(width: 40, height: 40, radius: 20)
                    .overlay(Circle().strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
                Text("How it will look on rosters and the auction screen").crichere(.sans(12.5, lineHeight: 16.9), CrichereColor.onSurfaceVariant)
            }
        }
    }

    private func regionImage(width: CGFloat, height: CGFloat, radius: CGFloat) -> some View {
        let r = region
        return Group {
            if let image = cropped(photo, CGRect(x: r.left, y: r.top, width: r.width, height: r.height)) {
                Image(uiImage: UIImage(cgImage: image)).resizable()
            } else {
                Color(hex: 0x101410)
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        .accessibilityHidden(true)
    }

    private var buttons: some View {
        HStack(spacing: 10) {
            Button(action: onChooseAnother) {
                Text("Choose another").crichere(.sans(14.5, .semiBold)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary, border: CrichereColor.outline, radius: 24))
            Button {
                if let onUseBanner { onUseBanner(region) } else { onUsePhoto(crop) }
            } label: {
                Text(useLabel).crichere(.sans(14.5, .semiBold)).frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
            }
            .buttonStyle(PillButtonStyle(fill: CrichereColor.primary, pressedFill: CrichereColor.greenPressed, content: CrichereColor.onPrimary, radius: 24))
        }
        .padding(.horizontal, 4)
    }
}
