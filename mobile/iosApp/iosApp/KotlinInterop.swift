import Foundation
import Shared

/// Swift `Data` -> Kotlin/Native `KotlinByteArray`, the standard manual bridging pattern (SKIE
/// does not auto-convert `Data`). Used by [JoinLeagueView]/[ClaimFranchiseView]'s screenshot/logo
/// upload calls -- see docs/PHASE3.md.
func toKotlinByteArray(_ data: Data) -> KotlinByteArray {
    let array = KotlinByteArray(size: Int32(data.count))
    data.withUnsafeBytes { (rawBuffer: UnsafeRawBufferPointer) in
        let bytes = rawBuffer.bindMemory(to: UInt8.self)
        for i in 0..<data.count {
            array.set(index: Int32(i), value: Int8(bitPattern: bytes[i]))
        }
    }
    return array
}
