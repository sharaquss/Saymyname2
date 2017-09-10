package com.android.szparag.saymyname.views.activities


import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetBehavior.BottomSheetCallback
import android.support.v4.view.GestureDetectorCompat
import android.support.v7.widget.AppCompatImageButton
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.android.szparag.saymyname.R
import com.android.szparag.saymyname.dagger.DaggerGlobalScopeWrapper
import com.android.szparag.saymyname.events.CameraPictureEvent
import com.android.szparag.saymyname.events.CameraPictureEvent.CameraPictureEventType.CAMERA_BYTES_RETRIEVED
import com.android.szparag.saymyname.events.CameraPictureEvent.CameraPictureEventType.CAMERA_SHUTTER_EVENT
import com.android.szparag.saymyname.presenters.RealtimeCameraPreviewPresenter
import com.android.szparag.saymyname.utils.ERROR_CAMERA_RENDERING_COMMAND_EXC
import com.android.szparag.saymyname.utils.ERROR_CAMERA_RENDERING_COMMAND_NULL
import com.android.szparag.saymyname.utils.ERROR_CAMERA_RETRIEVAL
import com.android.szparag.saymyname.utils.ERROR_COMPRESSED_BYTES_INVALID_SIZE
import com.android.szparag.saymyname.utils.bindView
import com.android.szparag.saymyname.utils.configureFocusMode
import com.android.szparag.saymyname.utils.createArrayAdapter
import com.android.szparag.saymyname.utils.getCameraHardwareInfo
import com.android.szparag.saymyname.utils.itemSelections
import com.android.szparag.saymyname.utils.logMethod
import com.android.szparag.saymyname.utils.setRotation
import com.android.szparag.saymyname.views.contracts.RealtimeCameraPreviewView
import com.android.szparag.saymyname.views.contracts.View.UserAlertMessage
import com.android.szparag.saymyname.views.widgets.FullscreenMessageInfo
import com.android.szparag.saymyname.views.widgets.SaymynameCameraShutterButton
import com.android.szparag.saymyname.views.widgets.overlays.BottomSheetSinglePhotoDetails
import com.android.szparag.saymyname.views.widgets.overlays.SaymynameFloatingWordsView
import com.jakewharton.rxbinding2.view.RxView
import hugo.weaving.DebugLog
import io.reactivex.Completable
import io.reactivex.Observable
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.inject.Inject

@Suppress("DEPRECATION") //because of Camera1 API
@DebugLog
class RealtimeCameraPreviewActivity : SaymynameBaseActivity<RealtimeCameraPreviewPresenter>(), RealtimeCameraPreviewView, Callback {

  val cameraSurfaceView: SurfaceView by bindView(R.id.surfaceview_realtime_camera)
  val buttonHamburgerMenu: AppCompatImageButton by bindView(R.id.button_menu_hamburger)
  val spinnerSwitchLanguage: Spinner by bindView(R.id.button_switch_language)
  lateinit var spinnerSwitchLanguageAdapter: ArrayAdapter<CharSequence>
  val spinnerSwitchModel: Spinner by bindView(R.id.button_switch_model)
  lateinit var spinnerSwitchModelAdapter: ArrayAdapter<CharSequence>
  val buttonHistoricalEntries: AppCompatImageButton by bindView(R.id.button_menu_charts)
  val buttonCameraShutter: SaymynameCameraShutterButton by bindView(
      R.id.button_shutter) //todo: refactor to just interface (CameraShutterButton)
  val floatingWordsView: SaymynameFloatingWordsView by bindView(
      R.id.view_floating_words) //todo: refactor so that there is only interface here
  val bottomSheetSinglePhotoDetails: BottomSheetSinglePhotoDetails by bindView(
      R.id.layout_single_photo_details)
  val fullscreenMessageInfo: FullscreenMessageInfo by bindView(R.id.fullscreen_message_info)
  var fullscreenMessageType: UserAlertMessage? = null
  lateinit var bottomSheetBehavioursSinglePhotoDetails: BottomSheetBehavior<View>
  lateinit var gestureDetector: GestureDetectorCompat

  //cannot be injected because of a listener attached to constructor
  private lateinit var textToSpeechClient: TextToSpeech
  @Inject lateinit override var presenter: RealtimeCameraPreviewPresenter
  private var cameraInstance: Camera? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    logMethod()
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_realtime_camera_preview)
  }

  override fun onStart() {
    super.onStart()
    DaggerGlobalScopeWrapper.getComponent(this).inject(
        this) //todo: find a way to generize them in Kotlin
    presenter.attach(this) //todo: find a way to generize them in Kotlin

    spinnerSwitchLanguageAdapter = createArrayAdapter(R.array.spinner_lang_data)
    spinnerSwitchModelAdapter = createArrayAdapter(R.array.spinner_model_data)
    spinnerSwitchLanguage.adapter = spinnerSwitchLanguageAdapter
    spinnerSwitchModel.adapter = spinnerSwitchModelAdapter
    bottomSheetBehavioursSinglePhotoDetails = BottomSheetBehavior.from(
        bottomSheetSinglePhotoDetails)
    bottomSheetBehavioursSinglePhotoDetails.isHideable = false
    bottomSheetBehavioursSinglePhotoDetails.peekHeight = 0
    bottomSheetBehavioursSinglePhotoDetails.setBottomSheetCallback(object : BottomSheetCallback() {
      override fun onSlide(bottomSheet: View, slideOffset: Float) {

      }

      override fun onStateChanged(bottomSheet: View, newState: Int) {
        when (newState) {
          BottomSheetBehavior.STATE_COLLAPSED -> {
            logMethod("STATE_COLLAPSED")
            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
              bottomSheetSinglePhotoDetails.background = resources.getDrawable(
                  R.color.saymyname_blue_alpha_light)
            }
          }
          BottomSheetBehavior.STATE_SETTLING -> {
            logMethod("STATE_SETTLING")
          }
          BottomSheetBehavior.STATE_HIDDEN -> {
            logMethod("STATE_HIDDEN")
          }
          BottomSheetBehavior.STATE_EXPANDED -> {
            logMethod("STATE_EXPANDED")
            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
              bottomSheetSinglePhotoDetails.background = resources.getDrawable(
                  R.color.saymyname_blue_light)
            }
          }
          BottomSheetBehavior.STATE_DRAGGING -> {
            logMethod("STATE_DRAGGING")
          }
        }
      }

    })

  }

  override fun onStop() {
    logMethod()
    presenter.detach()
    super.onStop()
  }


  override fun bottomSheetPeek() {
    bottomSheetBehavioursSinglePhotoDetails.peekHeight = 75
  }

  override fun bottomSheetFillData(imageBytes: ByteArray,
      textsOriginal: List<String>,
      textsTranslated: List<String>,
      dateTime: Long) {
    bottomSheetSinglePhotoDetails.setPhotoDetails(imageBytes, textsOriginal, textsTranslated,
        dateTime)
  }

  override fun bottomSheetUnpeek() {
    bottomSheetBehavioursSinglePhotoDetails.peekHeight = 0
  }


  override fun onUserTakePictureButtonClicked(): Observable<Any> {
    return RxView.clicks(buttonCameraShutter)
  }

  override fun onUserModelSwitchButtonClicked(): Observable<String> {
    return itemSelections(spinnerSwitchModel)
  }

  override fun onUserLanguageSwitchClicked(): Observable<String> {
    return itemSelections(spinnerSwitchLanguage)
  }

  override fun onUserHamburgerMenuClicked(): Observable<Any> {
    return RxView.clicks(buttonHamburgerMenu).doOnNext(
        { parentDrawerLayout.openDrawer(sideNavigationView) })
  }

  override fun onUserHistoricalEntriesClicked(): Observable<Any> {
    return RxView.clicks(buttonHistoricalEntries)
  }

  override fun initializeCameraPreviewSurfaceView(): Completable {
    logMethod()
    return Completable.fromAction {
      val holder = cameraSurfaceView.holder
      holder.addCallback(this)
      holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }
  }

  override fun retrieveHardwareBackCamera(): Completable {
    logMethod()
    return Completable.create { emitter ->
      cameraInstance = openHardwareBackCamera()
      cameraInstance?.let { emitter.onComplete() } ?: emitter.onError(ERROR_CAMERA_RETRIEVAL)
    }
  }

  override fun renderRealtimeCameraPreview(): Completable {
    logMethod()
    return Completable.create { emitter ->
      cameraInstance ?: emitter.onError(ERROR_CAMERA_RENDERING_COMMAND_NULL)
      try {
        cameraInstance?.let {
          it.setPreviewDisplay(cameraSurfaceView.holder)
          configureCameraDisplayOrientation(0)
          it.configureFocusMode()
          it.startPreview()
          emitter.onComplete()
        }
      } catch (exc: Throwable) {
        emitter.onError(ERROR_CAMERA_RENDERING_COMMAND_EXC)
        exc.printStackTrace()
      }
    }
  }

  //todo: get rid of cameraId, we only care about back-facing cam here
  private fun configureCameraDisplayOrientation(cameraId: Int) {
    logMethod()
    cameraInstance?.let {
      val info = getCameraHardwareInfo(cameraId)
      var degreesToRotate = 0
      when (this.windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_0 -> degreesToRotate = 0
        Surface.ROTATION_90 -> degreesToRotate = 90
        Surface.ROTATION_180 -> degreesToRotate = 180
        Surface.ROTATION_270 -> degreesToRotate = 270
      }

      //todo: add link to this answer (from so, duh)
      val degreesToRotateFinal: Int
      if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        degreesToRotateFinal = (360 - (info.orientation + degreesToRotate) % 360) % 360 //super haxxxx
      } else {
        degreesToRotateFinal = (info.orientation - degreesToRotate + 360) % 360
      }

      it.setRotation(degreesToRotateFinal)
      it.setDisplayOrientation(degreesToRotateFinal)
    }
  }


  override fun stopRenderingRealtimeCameraPreview() {
    logMethod()
  }

  override fun renderLoadingAnimation() {
    logMethod()
    floatingWordsView.renderLoadingHalo()
  }

  override fun stopRenderingLoadingAnimation() {
    logMethod()
    floatingWordsView.stopRenderingLoadingHalo()
  }

  override fun renderNonTranslatedWords(nonTranslatedWords: List<String>) {
    logMethod()
    floatingWordsView.renderAuxiliaryWords(nonTranslatedWords)
  }

  override fun renderTranslatedWords(translatedWords: List<String>) {
    logMethod()
    floatingWordsView.renderPrimaryWords(translatedWords)
  }

  override fun stopRenderingWords() {
    logMethod()
    floatingWordsView.clearWords()
  }

  private fun openHardwareBackCamera(): Camera? {
    logMethod()
    try {
      return Camera.open()
    } catch (exc: RuntimeException) {
      exc.printStackTrace()
      //todo: ...logging, show error, whatever
    }
    return null
  }

  override fun takePicture(): Observable<CameraPictureEvent> {
    logMethod()
    return Observable.create({ emitter ->
      cameraInstance?.takePicture(
          Camera.ShutterCallback { emitter.onNext(CameraPictureEvent(CAMERA_SHUTTER_EVENT)) },
          null,
          Camera.PictureCallback { data, _ ->
            cameraInstance?.startPreview()
            emitter.onNext(CameraPictureEvent(CAMERA_BYTES_RETRIEVED, data))
          }
      )
    })
  }

  override fun scaleCompressEncodePictureByteArray(pictureByteArray: ByteArray)
      : Observable<CameraPictureEvent> {
    return Observable.create { emitter ->
      try {
        val options = BitmapFactory.Options().apply {
          this.inPurgeable = true
          rescaleImageRequestFactor(8,
              this) //todo: refactor so that i can specify minimum res (600-720px) instead of scaling //todo: because i do not know how powerful user camera is
        }
        val scaledBitmap = BitmapFactory.decodeByteArray(pictureByteArray, 0, pictureByteArray.size,
            options)
        val compressedByteStream = ByteArrayOutputStream()
        scaledBitmap.compress(JPEG, 60, compressedByteStream)
        if (compressedByteStream.size() <= 0) emitter.onError(ERROR_COMPRESSED_BYTES_INVALID_SIZE)
        else emitter.onNext(CameraPictureEvent(compressedByteStream.toByteArray()))
      } catch (exc: Throwable) {
        emitter.onError(exc)
      }
    }
  }

  private fun rescaleImageRequestFactor(
      downScaleFactor: Int,
      bitmapOptions: BitmapFactory.Options): BitmapFactory.Options {
    bitmapOptions.inSampleSize = downScaleFactor
    return bitmapOptions
  }

  override fun initializeTextToSpeechClient(locale: Locale) {
    logMethod()
    textToSpeechClient = TextToSpeech(applicationContext, TextToSpeech.OnInitListener {
      status ->
      status.takeIf { code -> code != TextToSpeech.ERROR }?.run {
        textToSpeechClient.language = locale
      }
    })
  }

  override fun speakText(textToSpeak: String, flushSpeakingQueue: Boolean) {
    logMethod()
    textToSpeechClient.speak(textToSpeak,
        if (flushSpeakingQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null)
  }

  override fun initializeSuddenMovementDetection() {
    logMethod()
  }

  override fun onSuddenMovementDetected() {
    logMethod()
  }

  override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
    logMethod()
  }

  override fun surfaceDestroyed(holder: SurfaceHolder?) {
    logMethod()
  }

  override fun surfaceCreated(holder: SurfaceHolder?) {
    logMethod()
  }

  override fun renderUserAlertMessage(userAlertMessage: UserAlertMessage) {
    when (userAlertMessage) {
      UserAlertMessage.CAMERA_PERMISSION_ALERT -> {
        fullscreenMessageInfo.show(R.drawable.ic_action_camera_dark,
            R.string.dialog_alert_permission_camera)
        fullscreenMessageType = UserAlertMessage.CAMERA_PERMISSION_ALERT
      }
      UserAlertMessage.STORAGE_PERMISSION_ALERT -> {
        fullscreenMessageInfo.show(R.drawable.ic_action_camera_dark,
            R.string.dialog_alert_permission_storage)
        fullscreenMessageType = UserAlertMessage.STORAGE_PERMISSION_ALERT
      }
    }
  }

  override fun stopRenderUserAlertMessage(userAlertMessage: UserAlertMessage) {
    if (fullscreenMessageType == userAlertMessage)
      fullscreenMessageInfo.hide()
  }


}
