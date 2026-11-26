// The bid ladder (docs/PHASE22.md), same rule as backend auction/BidLadder.kt: bids start at the base
// price; each range covers [previous upTo (or base), upTo) with its own step; the bid increment covers
// everything above the last range. Returns the smallest ladder value strictly above `amount`.
export function nextBid(league, amount) {
  const base = Number(league.auctionBasePrice)
  if (amount == null || amount < base) return base
  let from = base
  for (const range of league.auctionBidRanges ?? []) {
    const upTo = Number(range.upTo)
    const step = Number(range.step)
    if (amount < upTo) return Math.min(upTo, from + (Math.floor((amount - from) / step) + 1) * step)
    from = upTo
  }
  const step = Number(league.auctionBidIncrement)
  return from + (Math.floor((amount - from) / step) + 1) * step
}
