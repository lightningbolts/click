#import "ClickDisposableRollFilter.h"

#import <CoreImage/CoreImage.h>
#import <UIKit/UIKit.h>

static CIImage *_Nullable ApplyColorControls(
    CIImage *input,
    CGFloat brightness,
    CGFloat saturation,
    CGFloat contrast
) {
    CIFilter *filter = [CIFilter filterWithName:@"CIColorControls"];
    if (filter == nil) {
        return input;
    }
    [filter setValue:input forKey:kCIInputImageKey];
    [filter setValue:@(brightness) forKey:kCIInputBrightnessKey];
    [filter setValue:@(saturation) forKey:kCIInputSaturationKey];
    [filter setValue:@(contrast) forKey:kCIInputContrastKey];
    return filter.outputImage ?: input;
}

static CIImage *_Nullable ApplyTemperature(CIImage *input, CGFloat targetKelvin) {
    CIFilter *filter = [CIFilter filterWithName:@"CITemperatureAndTint"];
    if (filter == nil) {
        return input;
    }
    [filter setValue:input forKey:kCIInputImageKey];
    [filter setValue:[CIVector vectorWithX:6500 Y:0] forKey:@"inputNeutral"];
    [filter setValue:[CIVector vectorWithX:targetKelvin Y:0] forKey:@"inputTargetNeutral"];
    return filter.outputImage ?: input;
}

static CIImage *_Nullable ApplySepia(CIImage *input, CGFloat intensity) {
    CIFilter *filter = [CIFilter filterWithName:@"CISepiaTone"];
    if (filter == nil) {
        return input;
    }
    [filter setValue:input forKey:kCIInputImageKey];
    [filter setValue:@(intensity) forKey:kCIInputIntensityKey];
    return filter.outputImage ?: input;
}

static CIImage *_Nullable ApplyVignette(CIImage *input, CGFloat intensity, CGFloat radius) {
    CIFilter *filter = [CIFilter filterWithName:@"CIVignette"];
    if (filter == nil) {
        return input;
    }
    [filter setValue:input forKey:kCIInputImageKey];
    [filter setValue:@(intensity) forKey:kCIInputIntensityKey];
    [filter setValue:@(radius) forKey:kCIInputRadiusKey];
    return filter.outputImage ?: input;
}

static CIImage *_Nullable ApplyGrayscale(CIImage *input) {
    CIFilter *filter = [CIFilter filterWithName:@"CIPhotoEffectMono"];
    if (filter == nil) {
        return ApplyColorControls(input, 0.0, 0.0, 1.0);
    }
    [filter setValue:input forKey:kCIInputImageKey];
    return filter.outputImage ?: input;
}

static CIImage *_Nullable ApplyFilterChain(CIImage *input, int filterIndex) {
    switch (filterIndex) {
        case 1:
            return ApplyTemperature(input, 6200.0);
        case 2:
            return ApplyTemperature(input, 4200.0);
        case 3:
            return ApplySepia(input, 0.82);
        case 4:
            return ApplyColorControls(input, 0.0, 1.0, 1.45);
        case 5:
            return ApplyColorControls(input, -0.35, 0.72, 1.0);
        case 6:
            return ApplyGrayscale(input);
        case 7:
            return ApplyColorControls(input, 0.0, 1.65, 1.0);
        case 8: {
            CIImage *golden = ApplySepia(input, 0.45);
            return ApplyColorControls(golden, 0.0, 1.18, 1.0);
        }
        case 9: {
            CIImage *moody = ApplyVignette(input, 0.85, 1.35);
            return ApplyColorControls(moody, 0.0, 1.0, 1.18);
        }
        default:
            return input;
    }
}

static CIImage *_Nullable DownscaleIfNeeded(CIImage *input, int maxDimension) {
    if (maxDimension <= 0) {
        return input;
    }
    CGRect extent = input.extent;
    CGFloat largest = MAX(extent.size.width, extent.size.height);
    if (largest <= maxDimension) {
        return input;
    }
    CGFloat scale = (CGFloat)maxDimension / largest;
    CIFilter *scaleFilter = [CIFilter filterWithName:@"CILanczosScaleTransform"];
    if (scaleFilter == nil) {
        return input;
    }
    [scaleFilter setValue:input forKey:kCIInputImageKey];
    [scaleFilter setValue:@(scale) forKey:kCIInputScaleKey];
    [scaleFilter setValue:@(1.0) forKey:kCIInputAspectRatioKey];
    return scaleFilter.outputImage ?: input;
}

NSData *ClickApplyDisposableRollPhotoEffect(
    NSData *jpegData,
    int filterIndex,
    int previewMaxDimension
) {
    if (filterIndex <= 0 || jpegData.length == 0) {
        return jpegData;
    }

    CIImage *input = [CIImage imageWithData:jpegData options:nil];
    if (input == nil) {
        return jpegData;
    }

    input = DownscaleIfNeeded(input, previewMaxDimension);

    CIImage *output = ApplyFilterChain(input, filterIndex);
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
