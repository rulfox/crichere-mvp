import SwiftUI

/// Design screen D2 (androidApp `ui/AreaPickerSheet.kt`): places (states / districts) with a search
/// box; the current selection is highlighted and scrolled into view. Picking one calls [onSelected].
/// Present inside `crichereSheet`.
struct AreaPickerSheet<Option: Hashable>: View {
    let title: String
    let searchHint: String
    let options: [Option]
    let selectedName: String?
    let optionLabel: (Option) -> String
    let onSelected: (Option) -> Void

    @State private var query = ""

    private var visible: [Option] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return q.isEmpty ? options : options.filter { optionLabel($0).range(of: q, options: .caseInsensitive) != nil }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .crichere(.sans(17, .semiBold), CrichereColor.onBackground)
                .padding(.leading, 18)
                .padding(.vertical, 12)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 10) {
                CrIcon("ic_search", size: 20, tint: CrichereColor.onSurfaceVariant)
                ZStack(alignment: .leading) {
                    if query.isEmpty {
                        Text(searchHint).crichere(.sans(14), CrichereColor.onSurfaceVariant).accessibilityHidden(true)
                    }
                    TextField("", text: $query)
                        .crichere(.sans(14), CrichereColor.onBackground)
                        .tint(CrichereColor.primary)
                        .textFieldStyle(.plain)
                        .autocorrectionDisabled()
                        .submitLabel(.search)
                        .accessibilityLabel(searchHint)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 48)
            .background(Color(hex: 0xE4E8DF), in: Capsule())
            .padding(.horizontal, 18)
            Spacer().frame(height: 8)
            ScrollViewReader { reader in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(visible, id: \.self) { option in
                            let name = optionLabel(option)
                            let selected = name == selectedName
                            Button { onSelected(option) } label: {
                                Text(name)
                                    .crichere(.sans(15, .medium), CrichereColor.onBackground)
                                    .frame(maxWidth: .infinity, minHeight: 50, maxHeight: 50, alignment: .leading)
                                    .padding(.horizontal, 14)
                                    .background(selected ? CrichereColor.primaryContainer : .clear, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            }
                            .buttonStyle(PressHighlight(shape: RoundedRectangle(cornerRadius: 12, style: .continuous)))
                            .padding(.horizontal, 8)
                            .id(option)
                            .accessibilityAddTraits(selected ? .isSelected : [])
                        }
                    }
                }
                .frame(maxHeight: 420)
                .fixedSize(horizontal: false, vertical: true)
                .onAppear {
                    // Open scrolled to the current selection, with a little context above it.
                    guard let index = options.firstIndex(where: { optionLabel($0) == selectedName }) else { return }
                    reader.scrollTo(options[max(0, index - 2)], anchor: .top)
                }
            }
        }
        .padding(.bottom, 16)
    }
}
