//import SwiftUI
//import SharedLogic
//import PhotosUI
//
//struct SingleCompressView: View {
//    @State private var targetKb: Float = 300.0
//    @State private var selectedItem: PhotosPickerItem? = nil
//    @State private var summary : String = ""
//    @State private var compressImg: [UInt8] = [UInt8]()
//    
//    
//    private var ics = ImageCompressionSource()
//    
//    @State private var showContent = false
//    var body: some View {
//        ScrollView {
//            VStack(alignment: .leading, spacing: 16) {
//                Text("Target size: \(Int(targetKb)) KB")
//                    .font(.headline)
//                
//                Slider(value: $targetKb, in: 50...1000, step: 1)
//                
//                PhotosPicker(selection: $selectedItem, matching: .images) {
//                    Text("Pick a photo and compress")
//                        .frame(maxWidth: .infinity)
//                        .padding()
//                        .foregroundColor(.white)
//                        .background(Color.blue)
//                        .cornerRadius(8)
//                }
//                .onChange(of: selectedItem) { _, newItem in
//                    if let newItem {
//                        Task {
//                            let bArr = try await convertPhotoPickerItemToByteArray(newItem)
//                            let signedBytes = bArr?.first.map { Int8(bitPattern: $0) }
//                            
//                            if let safeBytes = bArr {
//                                let kotlinArray = KotlinByteArray(size: Int32(safeBytes.count))
//                                for (index, byte) in safeBytes.enumerated() {
//                                    kotlinArray.set(index: Int32(index), value: Int8(bitPattern: byte))
//                                }
//                                await compressImage(byteArray: kotlinArray, targetKb: targetKb)
//                            }
//                            
//                            
//                        }
//                    }
//                }
//                
//                Text("Summary: \(summary)")
//                    .font(.headline)
//                
//                let uiImage = UIImage(data: Data(compressImg))
//                if let uiImage {
//                    Image(uiImage: uiImage)
//                        .resizable()
//                        .scaledToFit()
//                        .frame(maxWidth: .infinity)
//                }
//
//            }
//            .padding(16)
//        }
//    }
//    
//    
//    @MainActor
//    private func compressImage(byteArray: KotlinByteArray, targetKb: Float) async {
//        let respond = ics.compressImage(original: byteArray, targetKb: targetKb){state, err in
//            guard let stateX = state else {
//                return
//            }
//            switch (stateX) {
//            case let respond as CompressState.Done:
//                let swiftInt8Array: [Int8] = (0..<respond.preview.size).map { respond.preview.get(index: $0) }
//                let swiftBytes: [UInt8] = swiftInt8Array.map { UInt8(bitPattern: $0) }
//                
//                self.compressImg = swiftBytes
//                self.summary = respond.summary
//                print("\(respond.summary)")
//                
//            case let failed as CompressState.Failed:
//                print("\(failed.message)")
//            default:
//                print("default")
//            }
//        }
//    }
//    
//    func convertPhotoPickerItemToByteArray(_ item: PhotosPickerItem) async throws -> [UInt8]? {
//        guard let data = try await item.loadTransferable(type: Data.self) else {
//            return nil
//        }
//        let byteArray = [UInt8](data)
//        return byteArray
//    }
//}





import SwiftUI
import PhotosUI
import SharedLogic

struct SingleCompressView: View {

    // MARK: - State

    @State private var selectedItem: PhotosPickerItem?
    @State private var originalImage: IdentifiableImageData?
    @State private var compressedImage: IdentifiableImageData?
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
                    ProgressView("Loading image...")
                        .padding()
                }

                if let original = originalImage {
                    originalImageSection(original)
                }
                
                Text("Target size: \(Int(targetKb)) KB").font(.headline)
                Slider(value: $targetKb, in: 50...1000, step: 1)

                compressionButton

                if let compressed = compressedImage {
                    compressedImageSection(compressed)
                }

                if originalImage == nil && !isLoading {
                    ContentUnavailableView(
                        "No Photo Selected",
                        systemImage: "photo",
                        description: Text("Select a photo to start compression.")
                    )
                    .padding(.top, 30)
                }
            }
            .padding(.vertical)
        }
        .onChange(of: selectedItem) { _, newItem in
            guard let newItem = newItem else {
                return
            }

            Task {
                await loadImage(from: newItem)
            }
        }
    }

    // MARK: - Header

    private var headerView: some View {

        VStack(alignment: .leading, spacing: 8) {

            Text("Single Compression")
                .font(.largeTitle)
                .bold()

            Text("Select a photo, compress it, and compare the original and compressed sizes.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal)
    }

    // MARK: - Photo Picker

    private var photoPicker: some View {

        PhotosPicker(
            selection: $selectedItem,
            matching: .images
        ) {

            Label(
                originalImage == nil ? "Select Photo" : "Change Photo",
                systemImage: "photo.on.rectangle.angled"
            )
            .bold()
            .frame(maxWidth: .infinity)
            .padding()
            .background(.blue)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .padding(.horizontal)
    }

    // MARK: - Original Image Section

    private func originalImageSection(
        _ item: IdentifiableImageData
    ) -> some View {

        VStack(alignment: .leading, spacing: 15) {

            Text("Original Image")
                .font(.headline)

            Image(uiImage: item.image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity)
                .frame(maxHeight: 300)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .shadow(radius: 3)

            HStack {
                Label("Original Size", systemImage: "photo")

                Spacer()

                Text(item.sizeString)
                    .fontWeight(.semibold)
            }
            .font(.subheadline)
            .padding()
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .padding(.horizontal)
    }

    // MARK: - Compressed Image Section

    private func compressedImageSection(
        _ item: IdentifiableImageData
    ) -> some View {

        VStack(alignment: .leading, spacing: 15) {

            Text("Compressed Output")
                .font(.headline)
                .foregroundStyle(.green)

            Image(uiImage: item.image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity)
                .frame(maxHeight: 300)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay {
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.green.opacity(0.5), lineWidth: 2)
                }

            HStack {
                Label("Compressed Size", systemImage: "arrow.down.circle")

                Spacer()

                Text(item.sizeString)
                    .fontWeight(.bold)
                    .foregroundStyle(.green)
            }
            .font(.subheadline)
            .padding()
            .background(Color.green.opacity(0.10))
            .clipShape(RoundedRectangle(cornerRadius: 10))

            if let original = originalImage {
                savingsView(
                    originalSize: original.rawData.count,
                    compressedSize: item.rawData.count
                )
            }
        }
        .padding(.horizontal)
    }

    // MARK: - Savings

    private func savingsView(
        originalSize: Int,
        compressedSize: Int
    ) -> some View {

        let savedBytes = max(0, originalSize - compressedSize)

        let percentage = originalSize > 0
            ? Double(savedBytes) / Double(originalSize) * 100
            : 0

        return VStack(spacing: 8) {

            Text("Space Saved")
                .font(.headline)

            Text(String(format: "%.1f%%", percentage))
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(.green)

            Text("\(formatBytes(savedBytes)) saved")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color.green.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Compression Button

    private var compressionButton: some View {

        Button {

            Task {
                await compressImage()
            }

        } label: {

            HStack {

                if isCompressing {

                    ProgressView()
                        .tint(.white)

                    Text("Compressing...")

                } else {

                    Image(systemName: "arrow.down.right.and.arrow.up.left")

                    Text("Compress Image")
                }
            }
            .bold()
            .frame(maxWidth: .infinity)
            .padding()
            .background(
                isCompressing || originalImage == nil
                ? Color.gray
                : Color.green
            )
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .disabled(isCompressing || originalImage == nil)
        .padding(.horizontal)
    }

    // MARK: - Load Image

    @MainActor
    private func loadImage(from item: PhotosPickerItem) async {

        isLoading = true

        originalImage = nil
        compressedImage = nil

        defer {
            isLoading = false
        }

        do {

            guard let data = try await item.loadTransferable(
                type: Data.self
            ) else {
                return
            }

            guard let image = UIImage(data: data) else {
                return
            }

            originalImage = IdentifiableImageData(
                image: image,
                sizeString: formatBytes(data.count),
                rawData: data
            )

        } catch {
            print("Image loading error: \(error)")
        }
    }

    // MARK: - Swift Data -> Kotlin ByteArray

    private func toKotlinByteArray(
        _ data: Data
    ) -> KotlinByteArray {

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
                targetKb: self.targetKb
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
                                "Empty compression result"
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
                print("Invalid compressed image")
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

    // MARK: - Compress Image

    @MainActor
    private func compressImage() async {

        guard let original = originalImage else {
            return
        }

        isCompressing = true
        compressedImage = nil

        defer {
            isCompressing = false
        }

        do {

            // 1. Convert original Swift Data
            let kotlinInput = toKotlinByteArray(
                original.rawData
            )

            // 2. Call Kotlin suspend function
            let response = try await compressSingleImage(
                kotlinInput
            )

            // 3. Convert Kotlin response
            let result = convertResponse(response)

            // 4. Update UI
            compressedImage = result

        } catch {
            print("Compression error: \(error)")
        }
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
