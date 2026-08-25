// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "P2PML",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "P2PML", targets: ["P2PML"])
    ],
    targets: [
        .binaryTarget(
            name: "P2PML",
            url: "https://github.com/DimaDemchenko/p2pml-kmp/releases/download/0.1.0/P2PML.xcframework.zip",
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        )
    ]
)
