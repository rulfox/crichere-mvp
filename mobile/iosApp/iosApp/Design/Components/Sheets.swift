import SwiftUI

/// Material3 `ModalBottomSheet` as the Android screens configure it: wraps its content (no half
/// state), 28 pt top corners, a 32×4 drag handle 10 pt from the top, scrim, drag down or tap the
/// scrim to dismiss (unless [dismissable] is false, e.g. while a report is sending -- R5e).
struct CrichereSheetStyle {
    var background: Color = CrichereColor.background
    /// `0x800A120C`, what the crop, area-picker and screenshot-check sheets set; see [material].
    var scrim: Color = Color(hex: 0x0A120C, opacity: 0.5)
    var handle: Color = Color(hex: 0xB9C1B4)
    var showHandle: Bool = true
    /// Fill the screen height (minus the status bar) instead of wrapping the content.
    var fullHeight: Bool = false

    /// A `ModalBottomSheet` left on Material3's default scrim (black at 32%): the report sheet, My profile's photo sheet.
    static let material = CrichereSheetStyle(scrim: Color.black.opacity(0.32))
}

private struct BottomSheetContainer<Content: View>: View {
    let style: CrichereSheetStyle
    let dismissable: Bool
    let onDismiss: () -> Void
    @ViewBuilder let content: () -> Content

    @State private var shown = false
    @State private var drag: CGFloat = 0

    var body: some View {
        ZStack(alignment: .bottom) {
            style.scrim
                .ignoresSafeArea()
                .opacity(shown ? 1 : 0)
                .onTapGesture { if dismissable { close() } }
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Dismiss")
            VStack(spacing: 0) {
                if style.showHandle {
                    Capsule().fill(style.handle).frame(width: 32, height: 4).padding(.top, 10)
                        .accessibilityHidden(true)
                }
                content()
                    .frame(maxHeight: style.fullHeight ? .infinity : nil, alignment: .top)
            }
            .frame(maxWidth: .infinity)
            .background(
                UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                    .fill(style.background)
                    .ignoresSafeArea(edges: .bottom)
            )
            .padding(.top, style.fullHeight ? 12 : 0)
            .offset(y: shown ? max(0, drag) : 1000)
            .gesture(
                DragGesture()
                    .onChanged { value in if dismissable { drag = value.translation.height } }
                    .onEnded { value in
                        if dismissable && (value.translation.height > 120 || value.predictedEndTranslation.height > 300) {
                            close()
                        } else {
                            withAnimation(.spring(duration: 0.3)) { drag = 0 }
                        }
                    }
            )
            .accessibilityAddTraits(.isModal)
        }
        .onAppear { withAnimation(.spring(duration: 0.35, bounce: 0)) { shown = true } }
    }

    private func close() {
        withAnimation(.easeIn(duration: 0.2)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { onDismiss() }
    }
}

extension View {
    /// Shows [content] in a Material-style bottom sheet over the whole app.
    func crichereSheet<Content: View>(isPresented: Binding<Bool>, dismissable: Bool = true, style: CrichereSheetStyle = .init(),
                                      @ViewBuilder content: @escaping () -> Content) -> some View {
        crichereOverlay(isPresented: isPresented) {
            BottomSheetContainer(style: style, dismissable: dismissable, onDismiss: { isPresented.wrappedValue = false }, content: content)
        }
    }

    /// Item-driven variant: the sheet shows while [item] is non-nil.
    func crichereSheet<Item, Content: View>(item: Binding<Item?>, dismissable: Bool = true, style: CrichereSheetStyle = .init(),
                                            @ViewBuilder content: @escaping (Item) -> Content) -> some View {
        let shown = Binding(get: { item.wrappedValue != nil }, set: { if !$0 { item.wrappedValue = nil } })
        return crichereOverlay(isPresented: shown) {
            if let value = item.wrappedValue {
                BottomSheetContainer(style: style, dismissable: dismissable, onDismiss: { item.wrappedValue = nil }) { content(value) }
            }
        }
    }
}

/// Material3 `Checkbox`: an 18 pt box (2 pt border, 2 pt corners) in a 48 pt touch target; checked is
/// filled [checkedColor] with a white tick.
struct CrichereCheckbox: View {
    let checked: Bool
    var checkedColor: Color = CrichereColor.primary
    var uncheckedColor: Color = CrichereColor.inkSubtle
    var label: String = ""
    /// 48 = Material's minimum touch target; 24 = a checkbox drawn inside a row that does the toggling.
    var size: CGFloat = 48
    let onChange: (Bool) -> Void

    var body: some View {
        Button { onChange(!checked) } label: {
            ZStack {
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(checked ? checkedColor : .clear)
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .strokeBorder(checked ? checkedColor : uncheckedColor, lineWidth: 2)
                if checked {
                    Path { path in
                        path.move(to: CGPoint(x: 4, y: 9.2))
                        path.addLine(to: CGPoint(x: 7.4, y: 12.6))
                        path.addLine(to: CGPoint(x: 14, y: 5.6))
                    }
                    .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .square))
                }
            }
            .frame(width: 18, height: 18)
            .frame(width: size, height: size)
        }
        .buttonStyle(.pressCircle)
        .accessibilityLabel(label)
        .accessibilityValue(checked ? "Checked" : "Unchecked")
    }
}
