#import "ClickIosPickerLayout.h"

void ClickLayoutFullWidthDatePicker(UIDatePicker *picker) {
    if (picker == nil) {
        return;
    }
    picker.backgroundColor = UIColor.clearColor;
    picker.translatesAutoresizingMaskIntoConstraints = YES;
    picker.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
}

void ClickLayoutFullWidthTimePicker(UIDatePicker *picker) {
    ClickLayoutFullWidthDatePicker(picker);
}
