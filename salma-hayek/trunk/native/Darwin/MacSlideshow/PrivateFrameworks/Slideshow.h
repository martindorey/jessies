/*
 *     Generated by class-dump 3.0.
 *     class-dump is Copyright (C) 1997-1998, 2000-2001, 2004 by Steve Nygard.
 */
@interface Slideshow : NSResponder {
  id mPrivateData;
}

+ (id)sharedSlideshow;
+ (void)addImageToiPhoto:(id)fp8;
- (id)init;
- (void)dealloc;
- (void)setDataSource:(id)fp8;
- (void)loadConfigData;
- (void)runSlideshowWithDataSource:(id)fp8 options:(id)fp12;
- (void)startSlideshow:(id)fp8;
- (void)runSlideshowWithPDF:(id)fp8 options:(id)fp12;
- (void)stopSlideshow:(id)fp8;
- (void)noteNumberOfItemsChanged;
- (void)reloadData;
- (int)indexOfCurrentObject;
- (void)setAutoPlayDelay:(float)fp8;
- (void)mouseMoved:(id)fp8;
@end
