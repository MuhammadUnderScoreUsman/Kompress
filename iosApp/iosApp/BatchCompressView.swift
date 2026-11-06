import SwiftUI
import PhotosUI
import SharedLogic

struct BatchCompressView: View {

    // MARK: - State

    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var originalImagesData: [IdentifiableImageData] = []
    @State private var compressedImagesData: [IdentifiableImageData] = []
    @State private var targetKb: Float = 300.0
    @State private var isLoading = false
    @State private var isCompressing = false

    private let ics = ImageCompressionSource()

    // MARK: - Image Model

    struct IdentifiableImageData: Identifiable {
        let id = UUID()
        let image: UIImage
        let sizeString: String
        let rawData: Data
    }

    // MARK: - UI

    var body: some View {

        ScrollView {
            VStack(spacing: 25) {

                headerView

                photoPicker

                if isLoading {
                    ProgressView("Loading images...")
                }

                originalImagesSection

                Text("Target size: \(Int(targetKb)) KB").font(.headline)
                Slider(value: $targetKb, in: 50...1000, step: 1)

                
                compressionButton

                compressedImagesSection
            }
            .padding(.vertical)
        }
        .onChange(of: selectedItems) { _, newItems in
            Task {
                await loadImages(from: newItems)
            }
        }
    }

    // MARK: - Header

    private var headerView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Batch Compression")
                .font(.largeTitle)
                .bold()

            Text("Select multiple photos, compress them, and compare their sizes.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal)
    }

    // MARK: - Photo Picker

    private var photoPicker: some View {
        PhotosPicker(
            selection: $selectedItems,
            maxSelectionCount: 0,
            matching: .images
        ) {
            Label("Select Photos", systemImage: "photo.on.rectangle.angled")
                .bold()
                .frame(maxWidth: .infinity)
                .padding()
                .background(.blue)
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .padding(.horizontal)
    }

    // MARK: - Original Images

    private var originalImagesSection: some View {

        VStack(alignment: .leading, spacing: 15) {

            Text("Original Photos (\(originalImagesData.count))")
                .font(.headline)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 15) {
                    ForEach(originalImagesData) { item in
                        imageCard(item, isCompressed: false)
                    }
                }
            }
        }
        .padding(.horizontal)
    }

    // MARK: - Compressed Images

    private var compressedImagesSection: some View {

        Group {
            if !compressedImagesData.isEmpty {

                VStack(alignment: .leading, spacing: 15) {

                    Text("Compressed Output")
                        .font(.headline)
                        .foregroundStyle(.green)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 15) {
                            ForEach(compressedImagesData) { item in
                                imageCard(item, isCompressed: true)
                            }
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    // MARK: - Image Card

    private func imageCard(
        _ item: IdentifiableImageData,
        isCompressed: Bool
    ) -> some View {

        VStack(spacing: 8) {

            Image(uiImage: item.image)
                .resizable()
                .scaledToFill()
                .frame(width: 140, height: 140)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(
                            isCompressed ? Color.green : Color.clear,
                            lineWidth: 2
                        )
                }

            Text(item.sizeString)
                .font(.caption)
                .fontWeight(.semibold)
        }
        .frame(width: 140)
    }

    // MARK: - Compression Button

    private var compressionButton: some View {

        Button {
            Task {
                await compressImages()
            }
        } label: {

            HStack {
                if isCompressing {
                    ProgressView()
                        .tint(.white)

                    Text("Compressing...")
                } else {
                    Image(systemName: "arrow.down.right.and.arrow.up.left")

                    Text("Compress \(originalImagesData.count) Photos")
                }
            }
            .bold()
            .frame(maxWidth: .infinity)
            .padding()
            .background(isCompressing ? Color.gray : Color.green)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .disabled(isCompressing || originalImagesData.isEmpty)
        .padding(.horizontal)
    }

    // MARK: - Load Images

    @MainActor
    private func loadImages(from items: [PhotosPickerItem]) async {

        isLoading = true

        originalImagesData.removeAll()
        compressedImagesData.removeAll()

        for item in items {

            do {
                guard let data = try await item.loadTransferable(type: Data.self),
                      let image = UIImage(data: data) else {
                    continue
                }

                let model = IdentifiableImageData(
                    image: image,
                    sizeString: formatBytes(data.count),
                    rawData: data
                )

                originalImagesData.append(model)

            } catch {
                print("Image loading error: \(error)")
            }
        }

        isLoading = false
    }

    // MARK: - Swift Data -> Kotlin ByteArray

    private func toKotlinByteArray(_ data: Data) -> KotlinByteArray {

        let bytes = [UInt8](data)

        let kotlinBytes = KotlinByteArray(
            size: Int32(bytes.count)
        )

        for index in 0..<bytes.count {

            let signedByte = Int8(bitPattern: bytes[index])

            kotlinBytes.set(
                index: Int32(index),
                value: signedByte
            )
        }

        return kotlinBytes
    }

    // MARK: - Kotlin Suspend Function Bridge

    private func compressSingleImage(
        _ input: KotlinByteArray
    ) async throws -> CompressState {

        return try await withCheckedThrowingContinuation {
            (continuation: CheckedContinuation<CompressState, Error>) in

            ics.compressImage(
                original: input,
                targetKb: targetKb
            ) { state, error in

                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }

                guard let state = state else {

                    let error = NSError(
                        domain: "ImageCompression",
                        code: -1,
                        userInfo: [
                            NSLocalizedDescriptionKey:
                                "Kotlin returned an empty compression state."
                        ]
                    )

                    continuation.resume(throwing: error)
                    return
                }

                continuation.resume(returning: state)
            }
        }
    }

    // MARK: - Kotlin ByteArray -> Swift Data

    private func toSwiftData(
        _ kotlinBytes: KotlinByteArray
    ) -> Data {

        let count = Int(kotlinBytes.size)

        var bytes = [UInt8]()
        bytes.reserveCapacity(count)

        for index in 0..<count {

            let signedByte = kotlinBytes.get(
                index: Int32(index)
            )

            bytes.append(
                UInt8(bitPattern: signedByte)
            )
        }

        return Data(bytes)
    }

    // MARK: - Compress State -> Image Model

    private func convertResponse(
        _ state: CompressState
    ) -> IdentifiableImageData? {

        if let done = state as? CompressState.Done {

            let data = toSwiftData(done.preview)

            guard let image = UIImage(data: data) else {
                print("Invalid compressed image data")
                return nil
            }

            print(done.summary)

            return IdentifiableImageData(
                image: image,
                sizeString: formatBytes(data.count),
                rawData: data
            )
        }

        if let failed = state as? CompressState.Failed {
            print("Compression failed: \(failed.message)")
            return nil
        }

        print("Unknown compression state")

        return nil
    }

    // MARK: - Batch Compression

    @MainActor
    private func compressImages() async {

        guard !originalImagesData.isEmpty else {
            return
        }

        isCompressing = true
        compressedImagesData.removeAll()

        defer {
            isCompressing = false
        }

        var results: [IdentifiableImageData] = []

        for original in originalImagesData {

            do {

                // 1. Convert input
                let kotlinInput = toKotlinByteArray(
                    original.rawData
                )

                // 2. Call Kotlin suspend function
                let response = try await compressSingleImage(
                    kotlinInput
                )

                // 3. Convert response
                let compressed = convertResponse(response)

                // 4. Store result
                if let compressed = compressed {
                    results.append(compressed)
                }

            } catch {
                print("Compression error: \(error)")
            }
        }

        compressedImagesData = results
    }

    // MARK: - Formatting

    private func formatBytes(_ bytes: Int) -> String {

        let formatter = ByteCountFormatter()

        formatter.allowedUnits = [.useMB, .useKB]
        formatter.countStyle = .file

        return formatter.string(
            fromByteCount: Int64(bytes)
        )
    }
}
