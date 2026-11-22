import SwiftUI

/// One destination in [CrichereTabBar].
struct CrichereTab<ID: Hashable>: Identifiable {
    let id: ID
    let label: String
    let icon: String
    let selectedIcon: String
}

/// Bottom navigation per the design board (screen D) -- androidApp `AuthNavHost.kt` `MainBottomBar` on
/// Material3's `NavigationBar` geometry: an 80 pt tinted bar (#EDF0E8) that also fills the home-indicator
/// inset, a 64×32 pill indicator 12 pt from the top, 22 pt icons (filled when selected), labels 4 pt
/// under the pill, Bold when selected and Medium otherwise.
struct CrichereTabBar<ID: Hashable>: View {
    let tabs: [CrichereTab<ID>]
    let selected: ID
    let onSelect: (ID) -> Void

    private let selectedInk = CrichereColor.onPrimaryContainer
    private let unselectedInk = Color(hex: 0x4A564D)

    var body: some View {
        HStack(spacing: 8) {
            ForEach(tabs) { tab in
                let isSelected = tab.id == selected
                Button { onSelect(tab.id) } label: {
                    VStack(spacing: 4) {
                        CrIcon(isSelected ? tab.selectedIcon : tab.icon, size: 22, tint: isSelected ? selectedInk : unselectedInk)
                            .frame(width: 64, height: 32)
                            .background(isSelected ? CrichereColor.primaryContainer : .clear, in: Capsule())
                        Text(tab.label)
                            .crichere(.sans(11.5, isSelected ? .bold : .medium, lineHeight: 16), isSelected ? selectedInk : unselectedInk)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
                    .frame(height: 80, alignment: .top)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tab.label)
                .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
            }
        }
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity)
        .background(Color(hex: 0xEDF0E8).ignoresSafeArea(edges: .bottom))
        .animation(.easeOut(duration: 0.15), value: selected)
    }
}
