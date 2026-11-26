import SwiftUI
import Shared

/// Mirrors `MyLeaguesViewModel`'s shared `StateFlow<MyLeaguesState>` -- same pattern
/// `OwnProfileViewModelWrapper` documents.
@MainActor
final class MyLeaguesViewModelWrapper: ObservableObject {
    @Published var state: MyLeaguesState

    private let viewModel: MyLeaguesViewModel

    init() {
        let viewModel = KoinHelper().myLeaguesViewModel
        self.viewModel = viewModel
        self.state = viewModel.state.value

        Task { [weak self] in
            for await newState in viewModel.state {
                self?.state = newState
            }
        }
    }

    func retry() { viewModel.retry() }
}

/// iOS equivalent of `androidApp/.../ui/MyLeaguesScreen.kt`: four sections -- Organizing / Playing

/// My leagues (design M1-M3, androidApp `ui/MyLeaguesScreen.kt`): four stacked sections -- Organizing /
/// Playing / Franchise owner / Following -- with empty ones hidden; the M2 empty state and M3 error.
/// A refresh on re-entry keeps the last list on screen instead of flashing the spinner.
struct MyLeaguesView: View {
    let onOpenLeague: (String) -> Void
    var onBrowseLeagues: () -> Void = {}
    var onCreateLeague: () -> Void = {}

    @StateObject private var wrapper = MyLeaguesViewModelWrapper()

    var body: some View {
        MyLeaguesContent(state: wrapper.state, onOpenLeague: onOpenLeague, onRetry: wrapper.retry,
                         onBrowseLeagues: onBrowseLeagues, onCreateLeague: onCreateLeague)
            .onAppear { wrapper.retry() }
    }
}

/// The screen, stateless for the DEBUG parity gallery.
struct MyLeaguesContent: View {
    let state: MyLeaguesState
    var onOpenLeague: (String) -> Void = { _ in }
    var onRetry: () -> Void = {}
    var onBrowseLeagues: () -> Void = {}
    var onCreateLeague: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            Text("My leagues")
                .crichere(.archivo(28, .extraBold, lineHeight: 28, tracking: -0.84), CrichereColor.onBackground)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48, alignment: .leading)
                .padding(.horizontal, 19)
            content.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(CrichereColor.background.ignoresSafeArea())
    }

    @ViewBuilder private var content: some View {
        if let data = state.data {
            if data.organizing.isEmpty && data.playing.isEmpty && data.franchiseOwner.isEmpty && data.following.isEmpty {
                MyLeaguesEmptyState(onBrowseLeagues: onBrowseLeagues, onCreateLeague: onCreateLeague)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        section("Organizing", data.organizing, first: true)
                        section("Playing", data.playing, first: data.organizing.isEmpty)
                        section("Franchise owner", data.franchiseOwner, first: data.organizing.isEmpty && data.playing.isEmpty)
                        section("Following", data.following, first: data.organizing.isEmpty && data.playing.isEmpty && data.franchiseOwner.isEmpty)
                        Spacer().frame(height: 16)
                    }
                    .padding(.horizontal, 15)
                }
            }
        } else if state.isLoading {
            CrichereSpinner(color: CrichereColor.primary, lineWidth: 3, size: 35)
        } else {
            MyLeaguesLoadFailed(onRetry: onRetry)
        }
    }

    @ViewBuilder private func section(_ title: String, _ leagues: [LeagueSummaryDto], first: Bool) -> some View {
        if !leagues.isEmpty {
            Text(title)
                .crichere(.archivo(14.5, .bold, lineHeight: 14.5), CrichereColor.onBackground)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, first ? 4 : 12)
                .padding(.bottom, 8)
            // Unique across sections (Android keys "$title-${id}-$index"): a league can be in two lists.
            ForEach(leagues.indices.map { (key: "\(title)-\(leagues[$0].id)-\($0)", index: $0) }, id: \.key) { item in
                if item.index > 0 { Spacer().frame(height: 8) }
                MyLeagueRow(league: leagues[item.index]) { onOpenLeague(leagues[item.index].id) }
            }
        }
    }
}

private let rowDateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.locale = .current
    formatter.dateFormat = "d MMM yyyy"
    formatter.timeZone = TimeZone(identifier: "UTC")
    return formatter
}()

/// "2026-10-10" -> "10 Oct 2026" in the device locale (U4 D1), or the raw string if it isn't an ISO date.
private func rowDate(_ iso: String) -> String {
    let parser = DateFormatter()
    parser.locale = Locale(identifier: "en_US_POSIX")
    parser.timeZone = TimeZone(identifier: "UTC")
    parser.dateFormat = "yyyy-MM-dd"
    guard let date = parser.date(from: iso) else { return iso }
    return rowDateFormat.string(from: date)
}

/// 14 pt-rounded white row: 40 pt tile, two-line name, "<ground> · <district> · <date>" (only the
/// place truncates, never the date -- U4 M5), chevron.
private struct MyLeagueRow: View {
    let league: LeagueSummaryDto
    let onClick: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        Button(action: onClick) {
            HStack(spacing: 12) {
                LeagueTile(id: league.id, name: league.name, logoUrl: league.logoUrl, size: 40, corner: 10, fontSize: 11, logoRemoved: league.logoRemoved)
                VStack(alignment: .leading, spacing: 4) {
                    Text(league.name).crichere(.archivo(14, .bold, lineHeight: 16.1), CrichereColor.onBackground).lineLimit(2)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 0) {
                        Text("\(league.groundName) · \(league.district)").crichere(.sans(12, lineHeight: 14.4), CrichereColor.onSurfaceVariant)
                            .lineLimit(1)
                        Text(" · \(rowDate(league.startsOn))").crichere(.sans(12, lineHeight: 14.4), CrichereColor.onSurfaceVariant)
                            .lineLimit(1)
                            .fixedSize()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                CrIcon("ic_chevron_right", size: 20, tint: Color(hex: 0x9AA39C))
            }
            .padding(.horizontal, 15)
            .padding(.vertical, 13)
            .background(CrichereColor.surface, in: shape)
            .overlay(shape.strokeBorder(CrichereColor.outlineVariant, lineWidth: 1))
        }
        .buttonStyle(PressHighlight(shape: shape))
    }
}

/// M2 (app fix: this used to be a blank screen).
private struct MyLeaguesEmptyState: View {
    let onBrowseLeagues: () -> Void
    let onCreateLeague: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            CrIcon("ic_sports_cricket", size: 48, tint: CrichereColor.outline)
            Spacer().frame(height: 15)
            Text("No leagues yet").crichere(.archivo(18, .bold, lineHeight: 21.6), CrichereColor.onBackground)
            Spacer().frame(height: 10)
            Text("Join one as a player, claim a franchise, follow one, or start your own.")
                .crichere(.sans(13, lineHeight: 18.85), CrichereColor.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Spacer().frame(height: 18)
            HStack(spacing: 8) {
                Button(action: onBrowseLeagues) {
                    Text("Browse leagues").crichere(.sans(13.5, .semiBold, lineHeight: 13.5)).padding(.horizontal, 18).frame(height: 42)
                }
                .buttonStyle(PillButtonStyle(fill: CrichereColor.primary, pressedFill: CrichereColor.greenPressed, content: .white, radius: 21))
                Button(action: onCreateLeague) {
                    Text("Create").crichere(.sans(13.5, .semiBold, lineHeight: 13.5)).padding(.horizontal, 18).frame(height: 42)
                }
                .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary,
                                             border: CrichereColor.outline, radius: 21))
            }
        }
        .padding(.horizontal, 41)
        .padding(.bottom, 48)
    }
}

/// M3: error card with Retry.
private struct MyLeaguesLoadFailed: View {
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 10) {
                CrIcon("ic_cloud_off", size: 22, tint: CrichereColor.errorStrong)
                Text("Couldn't load your leagues.").crichere(.sans(13, .medium, lineHeight: 17.55), CrichereColor.errorBody)
                Spacer(minLength: 0)
            }
            .padding(14)
            .background(CrichereColor.errorContainer, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            Button(action: onRetry) {
                Text("Retry").crichere(.sans(13.5, .semiBold, lineHeight: 13.5)).padding(.horizontal, 22).frame(height: 44)
            }
            .buttonStyle(PillButtonStyle(fill: .clear, pressedFill: CrichereColor.primary.opacity(0.1), content: CrichereColor.primary,
                                         border: CrichereColor.outline, radius: 22))
        }
        .padding(.horizontal, 25)
        .padding(.bottom, 48)
    }
}
