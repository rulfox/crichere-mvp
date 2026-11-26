import SwiftUI

/// "Use my location" pill -- Profile setup (C) at the defaults, League creation (I9) at 18 pt / 13.5 pt
/// (androidApp `ProfileSetupScreen.kt` `LocationPill`): 44 pt outlined, or a spinner and "Finding your
/// location…" while locating.
struct LocationPill: View {
    let isLocating: Bool
    var iconSize: CGFloat = 19
    var fontSize: CGFloat = 14
    let action: () -> Void

    var body: some View {
        let label = CrichereTextStyle.sans(fontSize, .semiBold)
        Button(action: action) {
            HStack(spacing: 0) {
                if isLocating {
                    CrichereSpinner(color: CrichereColor.primary, size: 17)
                    Spacer().frame(width: 9)
                    Text("Finding your location…").crichere(label, CrichereColor.inkSubtle)
                } else {
                    CrIcon("ic_my_location", size: iconSize, tint: CrichereColor.primary)
                    Spacer().frame(width: 5)
                    Text("Use my location").crichere(label, CrichereColor.primary)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
        }
        .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary,
                                     border: isLocating ? CrichereColor.outlineDisabled : CrichereColor.outline, radius: 22))
        .disabled(isLocating)
    }
}
