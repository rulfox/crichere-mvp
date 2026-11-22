import SwiftUI

// SwiftUI twins of androidApp `ui/CrichereFields.kt`. The board's field (screens C, I, J, …): 54 pt
// box, 12 pt radius; the label sits inside as a placeholder until there's a value, then moves into a
// notch on the top border.

private enum FieldMode { case idle, active, disabled }

/// Per-screen look of a field. Profile setup (C) uses the defaults; League creation (I) uses [form]:
/// white fill, 52 pt, and a label notch that can sit on a white card.
struct FieldVariant {
    var height: CGFloat = 54
    var idleFill: Color = .clear
    /// What the notched label masks the border with: the screen, or a card behind the field.
    var notchColor: Color? = nil

    static let standard = FieldVariant()
    static let form = FieldVariant(height: 52, idleFill: .white)
    static let formOnCard = FieldVariant(height: 52, idleFill: .white, notchColor: .white)
}

private struct FieldFrame<Content: View, Supporting: View>: View {
    let label: String
    let hasValue: Bool
    let mode: FieldMode
    var look: FieldVariant = .standard
    var error: String? = nil
    var height: CGFloat? = nil
    var topAligned: Bool = false
    var reserveErrorSlot: Bool = false
    var supporting: Supporting?
    @ViewBuilder let content: () -> Content

    var body: some View {
        let (border, borderColor, fill): (CGFloat, Color, Color) = {
            if mode == .disabled { return (1, CrichereColor.fieldDisabledBorder, CrichereColor.fieldDisabledFill) }
            if error != nil { return (2, CrichereColor.error, mode == .active ? CrichereColor.surface : look.idleFill) }
            if mode == .active { return (2, CrichereColor.primary, CrichereColor.surface) }
            return (1, CrichereColor.outline, look.idleFill)
        }()
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: topAligned ? .topLeading : .leading) {
                content()
                    .padding(.leading, 14 + border)
                    .padding(.trailing, 11)
                    .padding(.top, topAligned ? 15 : 0)
            }
            .frame(maxWidth: .infinity, alignment: topAligned ? .topLeading : .leading)
            .frame(height: height ?? look.height, alignment: topAligned ? .topLeading : .leading)
            .background(fill, in: shape)
            .overlay(shape.strokeBorder(borderColor, lineWidth: border))
            .overlay(alignment: .topLeading) {
                if hasValue || error != nil {
                    // The notch masks the top border with whatever is behind the field.
                    Text(label)
                        .crichere(.sans(11, .medium, lineHeight: 11), notchLabelColor)
                        .padding(.horizontal, 4)
                        .background(look.notchColor ?? CrichereColor.background)
                        .offset(x: 11, y: -6)
                        .accessibilityHidden(true)
                }
            }
            .padding(.top, 7)

            if let supporting {
                supporting
                    .frame(maxWidth: .infinity, minHeight: 16, maxHeight: 16, alignment: .leading)
                    .padding(.top, 5)
            } else if reserveErrorSlot {
                // U4 F1: the 16 pt row is always there, so an error doesn't push the fields below down.
                ZStack(alignment: .leading) {
                    if let error {
                        Text(error).crichere(.sans(12, .medium, lineHeight: 16), CrichereColor.error).lineLimit(1).padding(.leading, 14)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 16, maxHeight: 16, alignment: .leading)
                .padding(.top, 5)
            } else if let error {
                Text(error).crichere(.sans(12, .medium, lineHeight: 15.6), CrichereColor.error).padding(.top, 4)
            }
        }
    }

    private var notchLabelColor: Color {
        if error != nil { return CrichereColor.error }
        if mode == .active { return CrichereColor.primary }
        return CrichereColor.onSurfaceVariant
    }
}

extension FieldFrame where Supporting == EmptyView {
    init(label: String, hasValue: Bool, mode: FieldMode, look: FieldVariant = .standard, error: String? = nil, height: CGFloat? = nil,
         topAligned: Bool = false, reserveErrorSlot: Bool = false, @ViewBuilder content: @escaping () -> Content) {
        self.init(label: label, hasValue: hasValue, mode: mode, look: look, error: error, height: height, topAligned: topAligned,
                  reserveErrorSlot: reserveErrorSlot, supporting: nil, content: content)
    }
}

private func placeholderStyle(_ size: CGFloat) -> CrichereTextStyle { .sans(size) }

private func placeholderColor(disabled: Bool, subtle: Bool) -> Color {
    if disabled { return CrichereColor.fieldDisabledText }
    if subtle { return CrichereColor.inkSubtle }
    return CrichereColor.onSurfaceVariant
}

private func valueStyle(_ size: CGFloat, mono: Bool = false) -> CrichereTextStyle {
    mono ? .mono(size, .medium) : .sans(size, .medium)
}

/// Text input in the board's notched-label style. [placeholder] replaces the label as the empty hint
/// (e.g. "name@bank"); [mono] sets numbers and IDs in JetBrains Mono; [minLines] > 1 makes a
/// top-aligned multi-line box of [multiLineHeight].
struct CrichereTextField: View {
    @Binding var text: String
    let label: String
    var look: FieldVariant = .standard
    var error: String? = nil
    var placeholder: String? = nil
    var mono: Bool = false
    var trailingIcon: String? = nil
    var minLines: Int = 1
    var multiLineHeight: CGFloat = 80
    var readOnly: Bool = false
    /// Keep a 16 pt error row under the field even without an error (no jump when one appears).
    var reserveErrorSlot: Bool = false
    /// Disabled look (board I16): grey fill/border/label, no typing.
    var enabled: Bool = true
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType? = nil
    var capitalization: TextInputAutocapitalization = .sentences
    var submitLabel: SubmitLabel = .done
    var onSubmit: () -> Void = {}
    /// Bind to observe/drive focus from the screen.
    var focus: FocusState<Bool>.Binding? = nil

    @FocusState private var ownFocus: Bool

    private var isFocused: Bool { focus?.wrappedValue ?? ownFocus }
    private var multiLine: Bool { minLines > 1 }

    var body: some View {
        FieldFrame(
            label: label,
            hasValue: !text.isEmpty || isFocused || placeholder != nil,
            mode: !enabled ? .disabled : (isFocused ? .active : .idle),
            look: look,
            error: error,
            height: multiLine ? multiLineHeight : nil,
            topAligned: multiLine,
            reserveErrorSlot: reserveErrorSlot
        ) {
            ZStack(alignment: multiLine ? .topLeading : .leading) {
                if text.isEmpty && (!isFocused || placeholder != nil) {
                    // An errored empty mono field shows its label in-box in mono too (board J2).
                    let size: CGFloat = multiLine ? 13.5 : 14.5
                    let style = (mono && error != nil && placeholder == nil) ? CrichereTextStyle.mono(size, .normal) : placeholderStyle(size)
                    Text(placeholder ?? label)
                        .crichere(style, placeholderColor(disabled: !enabled, subtle: placeholder != nil || error != nil))
                        .accessibilityHidden(true)
                }
                field
                    .padding(.trailing, trailingIcon != nil ? 30 : 0)
                if let trailingIcon {
                    CrIcon(trailingIcon, size: 20, tint: CrichereColor.onSurfaceVariant)
                        .padding(.trailing, 5)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if enabled && !readOnly { setFocus(true) } }
    }

    @ViewBuilder private var field: some View {
        let style = multiLine ? CrichereTextStyle.sans(13.5, .normal, lineHeight: 18.9) : valueStyle(14.5, mono: mono)
        let base = TextField("", text: $text, axis: multiLine ? .vertical : .horizontal)
            .crichere(style, CrichereColor.onBackground)
            .tint(CrichereColor.primary)
            .textFieldStyle(.plain)
            .keyboardType(keyboard)
            .textContentType(contentType)
            .textInputAutocapitalization(capitalization)
            .autocorrectionDisabled(mono || keyboard != .default)
            .submitLabel(submitLabel)
            .onSubmit(onSubmit)
            .disabled(!enabled || readOnly)
            .lineLimit(multiLine ? minLines...1000 : 1...1)
            .accessibilityLabel(label)
        if let focus {
            base.focused(focus)
        } else {
            base.focused($ownFocus)
        }
    }

    private func setFocus(_ value: Bool) {
        if let focus { focus.wrappedValue = value } else { ownFocus = value }
    }
}

/// A field that opens something on tap (a date picker) instead of taking text.
struct CrichereTapField<Supporting: View>: View {
    let label: String
    let value: String?
    var look: FieldVariant = .standard
    var error: String? = nil
    var trailingIcon: String? = nil
    var valueSize: CGFloat = 14
    /// Makes [trailingIcon] its own 40 pt button (a clear "x"), separate from the field's tap.
    var onTrailingClick: (() -> Void)? = nil
    var trailingLabel: String? = nil
    var trailingSize: CGFloat = 18
    var supporting: Supporting?
    let onClick: () -> Void

    var body: some View {
        FieldFrame(label: label, hasValue: value != nil, mode: .idle, look: look, error: error, supporting: supporting) {
            ZStack(alignment: .leading) {
                if let value {
                    Text(value).crichere(valueStyle(valueSize), CrichereColor.onBackground).lineLimit(1)
                        .padding(.trailing, onTrailingClick != nil ? 36 : 26)
                } else {
                    Text(label).crichere(placeholderStyle(14.5), placeholderColor(disabled: false, subtle: error != nil)).lineLimit(1)
                }
                if let trailingIcon, let onTrailingClick {
                    // The frame pads 11 pt on the right; the board's button sits 6 pt from the edge.
                    Button(action: onTrailingClick) {
                        CrIcon(trailingIcon, size: trailingSize, tint: CrichereColor.onSurfaceVariant).frame(width: 40, height: 40)
                    }
                    .buttonStyle(.pressCircle)
                    .accessibilityLabel(trailingLabel ?? "")
                    .offset(x: 5)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                } else if let trailingIcon {
                    CrIcon(trailingIcon, size: trailingSize, tint: CrichereColor.onSurfaceVariant)
                        .padding(.trailing, 3)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClick)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(value ?? "")")
        .accessibilityAddTraits(.isButton)
    }
}

extension CrichereTapField where Supporting == EmptyView {
    init(label: String, value: String?, look: FieldVariant = .standard, error: String? = nil, trailingIcon: String? = nil,
         valueSize: CGFloat = 14, onTrailingClick: (() -> Void)? = nil, trailingLabel: String? = nil, trailingSize: CGFloat = 18,
         onClick: @escaping () -> Void) {
        self.init(label: label, value: value, look: look, error: error, trailingIcon: trailingIcon, valueSize: valueSize,
                  onTrailingClick: onTrailingClick, trailingLabel: trailingLabel, trailingSize: trailingSize, supporting: nil, onClick: onClick)
    }
}

/// Tap-to-open picker in the board's field style (screen C5): the arrow flips up and the field takes
/// the focused look while its menu is open; the current selection is highlighted in the menu.
struct CrichereSelectField<Option: Hashable>: View {
    let label: String
    let options: [Option]
    let selected: Option?
    let optionLabel: (Option) -> String
    let onSelected: (Option) -> Void
    var enabled: Bool = true
    var fontSize: CGFloat = 14.5
    var look: FieldVariant = .standard
    var error: String? = nil

    @State private var expanded = false
    @State private var anchor: CGRect = .zero

    var body: some View {
        let arrowTint: Color = !enabled ? CrichereColor.fieldDisabledText
            : expanded ? CrichereColor.primary
            : selected != nil ? CrichereColor.onBackground : CrichereColor.onSurfaceVariant
        FieldFrame(label: label, hasValue: selected != nil, mode: !enabled ? .disabled : (expanded ? .active : .idle), look: look, error: error) {
            ZStack(alignment: .leading) {
                if let selected {
                    Text(optionLabel(selected)).crichere(valueStyle(fontSize), CrichereColor.onBackground)
                        .lineLimit(1).truncationMode(.tail).padding(.trailing, 26)
                } else {
                    Text(label).crichere(placeholderStyle(fontSize), placeholderColor(disabled: !enabled, subtle: error != nil)).lineLimit(1)
                }
                CrIcon(expanded ? "ic_arrow_drop_up" : "ic_arrow_drop_down", size: 22, tint: arrowTint)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .background(GeometryReader { proxy in
            Color.clear
                .onAppear { anchor = proxy.frame(in: .global) }
                .onChange(of: proxy.frame(in: .global)) { _, frame in anchor = frame }
        })
        .contentShape(Rectangle())
        .onTapGesture { if enabled { expanded = true } }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(selected.map(optionLabel) ?? "")")
        .accessibilityAddTraits(.isButton)
        .crichereOverlay(isPresented: $expanded) {
            DropdownMenuOverlay(anchor: anchor, options: options, selected: selected, optionLabel: optionLabel, onDismiss: { expanded = false }) { option in
                expanded = false
                onSelected(option)
            }
        }
    }
}

/// Material `DropdownMenu` under an anchor (4 pt below, same width): 12 pt corners, white, elevation 8,
/// 8 pt top/bottom padding, 40 pt rows. Flips above the anchor when there's more room there.
struct DropdownMenuOverlay<Option: Hashable>: View {
    let anchor: CGRect
    let options: [Option]
    let selected: Option?
    let optionLabel: (Option) -> String
    let onDismiss: () -> Void
    let onSelect: (Option) -> Void

    var body: some View {
        ZStack {
            Color.clear
                .contentShape(Rectangle())
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Dismiss menu")
            GeometryReader { proxy in
                // The safe region in window coordinates; the menu stays inside it.
                let screen = proxy.frame(in: .global)
                let content = CGFloat(options.count) * 40 + 16
                let below = screen.maxY - anchor.maxY - 4 - 8
                let above = anchor.minY - 4 - screen.minY - 8
                let showBelow = content <= below || below >= above
                let height = min(content, max(56, showBelow ? below : above))
                menu(height: height)
                    .offset(x: anchor.minX - screen.minX, y: (showBelow ? anchor.maxY + 4 : anchor.minY - 4 - height) - screen.minY)
            }
        }
    }

    private func menu(height: CGFloat) -> some View {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(options, id: \.self) { option in
                            Button { onSelect(option) } label: {
                                Text(optionLabel(option))
                                    .crichere(.sans(14), CrichereColor.onBackground)
                                    .frame(maxWidth: .infinity, minHeight: 40, maxHeight: 40, alignment: .leading)
                                    .padding(.horizontal, 16)
                                    .background(option == selected ? CrichereColor.primaryContainer : .clear)
                            }
                            .buttonStyle(.pressHighlight)
                            .accessibilityAddTraits(option == selected ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 8)
                }
                .scrollBounceBehavior(.basedOnSize)
                .frame(width: anchor.width, height: height)
                .background(CrichereColor.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
    }
}
