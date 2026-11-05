import PhotosUI
import SwiftUI


extension PhotosPickerItem {
    
    func pickerToByteArray() async throws -> [UInt8]? {
        
        guard let data = try await self.loadTransferable(type: Data.self) else {
            return nil
        }
        let byteArray = [UInt8](data)
        return byteArray
        
    }
}
