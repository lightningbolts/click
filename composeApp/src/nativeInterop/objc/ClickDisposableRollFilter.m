#import "ClickDisposableRollFilter.h"

#import <CoreImage/CoreImage.h>
#import <UIKit/UIKit.h>

NSData *ClickApplyDisposableRollPhotoEffect(NSData *jpegData, NSString *_Nullable effectName) {
    if (jpegData.length == 0) {
        return jpegData;
    }
    if (effectName == nil || effectName.length == 0) {
        return jpegData;
    }

    UIImage *image = [UIImage imageWithData:jpegData];
    if (image == nil || image.CGImage == nil) {
        return jpegData;
    }

    CIImage *input = [CIImage imageWithCGImage:image.CGImage];
    CIFilter *filter = [CIFilter filterWithName:effectName];
    if (filter == nil) {
        return jpegData;
    }

    [filter setValue:input forKey:kCIInputImageKey];
    CIImage *output = filter.outputImage;
    if (output == nil) {
        return jpegData;
    }

    CIContext *context = [CIContext contextWithOptions:nil];
    CGImageRef rendered = [context createCGImage:output fromRect:output.extent];
    if (rendered == nil) {
        return jpegData;
    }

    UIImage *filtered = [UIImage imageWithCGImage:rendered];
    CGImageRelease(rendered);

    NSData *jpeg = UIImageJPEGRepresentation(filtered, 0.88);
    return jpeg ?: jpegData;
}
