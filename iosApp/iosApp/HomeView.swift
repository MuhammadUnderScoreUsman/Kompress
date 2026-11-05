import SwiftUI

struct HomeView: View {
    
    @State private var navigationPath = NavigationPath()
    
    var body: some View {
        NavigationStack(path: $navigationPath) {
            VStack(spacing: 24) {
                Spacer()
                
                // Batch Button
                Button(action: {
                    handleBatchCompression()
                }) {
                    HStack {
                        Image(systemName: "square.grid.3x3.fill")
                        Text("Batch Compression")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(12)
                }
                
                // Single Button
                Button(action: {
                    handleSingleCompression()
                }) {
                    HStack {
                        Image(systemName: "doc.fill")
                        Text("Single Compression")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.secondary)
                    .cornerRadius(12)
                }
                
                Spacer()
            }
            .padding(.horizontal, 24)
            .navigationTitle("Image Compression Lib - iOS")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: CompressionRoute.self) { route in
                switch route {
                case .batch:
                    BatchCompressView()
                case .single:
                    SingleCompressView()
                }
            }
        }
    }
    
    private func handleBatchCompression() {
        navigationPath.append(CompressionRoute.batch)
    }
    
    private func handleSingleCompression() {
        navigationPath.append(CompressionRoute.single)
    }
}
