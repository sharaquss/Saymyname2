package com.android.szparag.saymyname.presenters

import com.android.szparag.saymyname.models.RealtimeCameraPreviewModel
import com.android.szparag.saymyname.utils.logMethod
import com.android.szparag.saymyname.utils.subListSafe
import com.android.szparag.saymyname.views.contracts.RealtimeCameraPreviewView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers

/**
 * Created by Przemyslaw Jablonski (github.com/sharaquss, pszemek.me) on 7/4/2017.
 */
class SaymynameRealtimeCameraPreviewPresenter(
    override val model: RealtimeCameraPreviewModel
) : BasePresenter(), RealtimeCameraPreviewPresenter {

  val viewSubscription: CompositeDisposable = CompositeDisposable()
  val modelSubscription: Disposable? = null

  //todo: this is fucked up
  fun getView(): RealtimeCameraPreviewView? {
    return this.view as RealtimeCameraPreviewView? //todo: why view can be nullable?
  }

  override fun onAttached() {
    super.onAttached()
    model.attach(this)
    initializeTextToSpeechClient()
    observeView()
    observeNewWords()
  }

  private fun observeView() {
    viewSubscription.add(
        getView()?.onUserTakePictureButtonClicked()
            ?.subscribeOn(AndroidSchedulers.mainThread())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.doOnNext {
              getView()?.stopRenderingWords()
            }
            ?.subscribe {
              onUserTakePictureButtonClicked()
            }
    )
  }


  override fun observeNewWords() {
    model.observeNewWords()
        .doOnEach { logMethod() }
        .subscribeBy(
            onNext = {

            },
            onError = {
              //todo: tutaj eventbusowanie
            }
        )
  }


  override fun onBeforeDetached() {
    logMethod()
    super.onBeforeDetached()
    viewSubscription.clear()
    modelSubscription?.dispose()
    getView()?.stopRenderingLoadingAnimation()
    getView()?.stopRenderingRealtimeCameraPreview()
  }

  override fun onViewReady() {
    logMethod()
    initializeCameraPreviewView()
  }


  override fun initializeCameraPreviewView() {
    logMethod()
    getView()?.let {
      it.initializeCameraPreviewSurfaceView()
      it.retrieveHardwareBackCamera()
      it.renderRealtimeCameraPreview()
    }
  }

  override fun onCameraPreviewViewInitialized() {
    logMethod()
  }

  override fun onCameraPreviewViewInitializationFailed() {
    logMethod()
  }

  override fun initializeTextToSpeechClient() {
    logMethod()
    getView()?.initializeTextToSpeechClient()
  }

  override fun startCameraRealtimePreview() {
    logMethod()
  }

  override fun onUserTakePictureButtonClicked() {
    logMethod()
//    getView()?.stopRenderingWords()
//    takeCameraPicture()
    getView()?.takePicture()
  }

  override fun takeCameraPicture() {
    logMethod()
  }

  override fun onCameraPhotoTaken() {
    logMethod()
    //todo: fire up more dense Google'y Vision animations
    //todo: fire up one-shot that symbolizes camera flash on shutter click
    //todo: fire up loading bar at the bottom
    //todo: hide bottom sheet if exists (bottom loading bar goes there)
  }

  override fun onCameraPhotoByteArrayReady(photoByteArray: ByteArray) {
    logMethod()
    //todo: make user feel that he is (almost) half of the way in image processing
    //todo: (take photo -> process -> send to clarifai -> send to translator)
    //todo: change loading bar colour maybe
    getView()?.scaleCompressEncodePictureByteArray(photoByteArray)
        ?.subscribeOn(Schedulers.computation())
        ?.observeOn(AndroidSchedulers.mainThread())
        ?.subscribeBy(
            onNext = {

            },
            onError = {

            }
        )
  }

  override fun requestImageVisionData(imageByteArray: ByteArray) {
    logMethod()
    model.requestImageProcessing("aaa03c23b3724a16a56b629203edc62c", imageByteArray, -1, -1)
        .subscribeBy(onComplete = {},
            onError = {})
//        .doOnEach { logMethod() }
//        .subscribe({
//          nonTranslatedWords ->
//          getView()?.let {
//            it.renderNonTranslatedWords(nonTranslatedWords)
//            requestTranslation(nonTranslatedWords)
//            nonTranslatedWords.forEach { word -> it.speakText(word) }
//          }
//        })
  }

  override fun requestTranslation(textsToTranslate: List<String>) {
    logMethod()
    model.requestTranslation("en-it", textsToTranslate.subListSafe(0, 4))
//        .doOnEach { logMethod() }
//        .subscribe({
//          translatedWords ->
//          getView()?.renderTranslatedWords(translatedWords)
//        })
  }

}