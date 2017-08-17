package com.example.android.softkeyboard;

import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class SoftKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener {
    static final boolean DEBUG = false;
    static final boolean PROCESS_HARD_KEYS = true;

    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;

    private StringBuilder mComposing = new StringBuilder();
    private StringBuilder dataForonUpdateSelection = new StringBuilder();
    private StringBuffer dataForCommitInputCurrection = new StringBuffer();

    private boolean flagForonUpdateSelection;

    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;

    private LatinKeyboard mCurKeyboard;

    private String mWordSeparators;


    NotificationManager imeChangeNotification;
    static final int uniqueIDForIMEChanges = 1394885;
    //static final int uniqueIDForExpire = 123987456;

    boolean flagForSpecialCharContainsNewLine = false;
    boolean flagForOnOffDictInPassField = false;
    boolean flagForTypeVariationWebEditText;

    /**
     * Main initialization of the input method component. Be sure to call to
     * super class.
     */

    @SuppressWarnings("deprecation")
    @Override
    public void onDestroy() {
        super.onDestroy();
        notifyMethodName("onDestroy");

        // Notification
       /* Intent intent = new Intent(this, AboutUsContentActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        String body = "This is a message from Avanish, thanks for your support";
        String title = "Avanish S.";
        Notification notify = new Notification(R.drawable.sym_keyboard_done, body, System.currentTimeMillis());
        notify.setLatestEventInfo(this, title, body, pi);
        notify.defaults = Notification.DEFAULT_ALL;
        imeChangeNotification.notify(uniqueIDForIMEChanges, notify);*/

    }

    @Override
    public void onCreate() {
        super.onCreate();
        notifyMethodName("onCreate");

        imeChangeNotification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        imeChangeNotification.cancel(uniqueIDForIMEChanges);
        mWordSeparators = getResources().getString(R.string.word_separators);
    }

    public void notifyMethodName(String str) {
        //Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG).show();
        System.out.println("lifecyle :"+str);
    }

    /**
     * This is the point where you can do all of your UI initialization. It is
     * called after creation and any configuration change.
     */
    @Override
    public void onInitializeInterface() {
        notifyMethodName("onInitializeInterface");
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets
            // recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth)
                return;
            mLastDisplayWidth = displayWidth;
        }
        mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
    }

    /**
     * Called by the framework when your view for creating input needs to be
     * generated. This will be called the first time your input method is
     * displayed, and every time it needs to be re-created such as due to a
     * configuration change.
     */
    @Override
    public View onCreateInputView() {
        notifyMethodName("onCreateInputView");
        mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mQwertyKeyboard);
        return mInputView;
    }

    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        notifyMethodName("onComputeInsets");
        if (!(isFullscreenMode())) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }
    }

    /**
     * Called by the framework when your view for showing candidates needs to be
     * generated, like {@link #onCreateInputView}.
     */
    @Override
    public View onCreateCandidatesView() {
        notifyMethodName("onCreateCandidatesView");
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application. At this point we have been bound to
     * the client, and are now receiving all of the detailed information about
     * the target of our edits.
     */
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
         notifyMethodName("OnStart");

        // Reset our state. We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any
        // way.

        mComposing.setLength(0);
        updateCandidates();

        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }
        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        // We are now going to initialize our state based on the type of
        // text being edited.
        System.out.println("EditorINfo val is: " + attribute.inputType);

        switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing. We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).

                mCurKeyboard = mQwertyKeyboard;
                mPredictionOn = true;
                flagForOnOffDictInPassField = false;
                flagForTypeVariationWebEditText = false;

                // We now look for a few special variations of text that will
                // modify our behavior.

                int variation = attribute.inputType & EditorInfo.TYPE_MASK_VARIATION;

                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                        || variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD

                        ) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    flagForOnOffDictInPassField = true;
                    mPredictionOn = false;
                }

                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    flagForOnOffDictInPassField = true;
                    mPredictionOn = false;

                }

                if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                    flagForTypeVariationWebEditText = true;
                    mPredictionOn = false;
                }
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own. We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    flagForOnOffDictInPassField = true;
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field. We can use this to
     * reset our state.
     */
    @Override
    public void onFinishInput() {
        super.onFinishInput();
        notifyMethodName("OnFinished");

        removeUpdatePressOnKeyBack = true;

        // Clear current composing text and candidates.
        dataForCommitInputCurrection.setLength(0);
        dataForonUpdateSelection.setLength(0);

        mComposing.setLength(0);
        updateCandidates();

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);

        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        notifyMethodName("OnStartInputView");
        removeUpdatePressOnKeyBack = false;
	/*	Calendar c = Calendar.getInstance();
		int month = c.get(Calendar.MONTH) + 1;
		int Year = c.get(Calendar.YEAR);
		int date = c.get(Calendar.DATE);
		int day = c.get(Calendar.DAY_OF_WEEK);

		if (Year == 2017 && day % 2 == 0 && date > 20 && month == 12 && tDay != day) {
			updateFlag = true;
			tDay = day;
		}

		if (updateFlag) {

			Intent intent = new Intent(this, AboutUsContentActivity.class);
			PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
			String body = "This is message for Expire our product in few days";
			String title = "Renew our Product";
			Notification n = new Notification(R.drawable.sym_keyboard_done, body, System.currentTimeMillis());
			n.setLatestEventInfo(this, title, body, pi);
			n.defaults = Notification.DEFAULT_ALL;
			imeChangeNotification.notify(uniqueIDForExpire, n);

			updateFlag = false;
		}*/

        // mUpdateInputEditText.setLength(0);
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        notifyMethodName("onConfigurationChanged");

        strForAddtoDict = "";// this assignment for TouchToSave clearing
        try {
            if (dataForCommitInputCurrection.length() > 0) {
                getCurrentInputConnection().commitText(dataForCommitInputCurrection,
                        dataForCommitInputCurrection.length() - dataForCommitInputCurrection.length() + 1);
            }
            updateCandidates();
        } catch (Exception e) {
        }
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart,
                                  int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        notifyMethodName("onUpdateSelection");

        dataForonUpdateSelection.setLength(0);
        if (mComposing.length() == 0) {
            dataForCommitInputCurrection.setLength(0);
        }

        try {
            if (!getCurrentInputConnection().getTextBeforeCursor(1, 0).equals(" ")&& !flagForTypeVariationWebEditText) {

                String textBefore = getCurrentInputConnection().getTextBeforeCursor(25, 0).toString();

                // **********************************************
                String newLineTestStr = textBefore;
                String[] str = newLineTestStr.split(" ");
                if (str.length > 0) {
                    newLineTestStr = str[str.length - 1];
                }
                if (newLineTestStr.contains("\n")) {
                    flagForSpecialCharContainsNewLine = true;
                }
                // **********************************************
                String[] strTemp = StringToken.spliteMethods(textBefore.replace("\n", " "), "   ");

                if (strTemp.length > 0) {
                    String temp = str[str.length - 1];
                    if (temp.contains("\n")) {
                        flagForSpecialCharContainsNewLine = false;
                        if (temp.endsWith("\n")) {
                            textBefore = " ";
                        } else {
                            String ss = strTemp[strTemp.length - 1];
                            textBefore = ss;
                        }
                    } else {
                        String ss = strTemp[strTemp.length - 1];
                        textBefore = ss;
                    }
                }

                if (!(textBefore.equals(" "))) {
                    flagForonUpdateSelection = true;
                    dataForonUpdateSelection.setLength(0);
                    dataForonUpdateSelection.append(textBefore);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    updateCandidates();
                }

            } else {
                dataForonUpdateSelection.setLength(0);
                updateCandidates();
            }
        } catch (Exception e) {

        }

        if (mComposing.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }

    }

    /**
     * This tells us about completions that the editor has determined based on
     * the current text in it. We want to use this in fullscreen mode to show
     * the completions ourself, since the editor can not be seen in that
     * situation.
     */
    @Override
    public void onDisplayCompletions(CompletionInfo[] completions) {
         notifyMethodName("OnDisplayCompletions");
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }

            List<String> stringList = new ArrayList<String>();
            for (int i = 0; i < (completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null)
                    stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection. It is only needed when using the PROCESS_HARD_KEYS
     * option.
     */

    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        notifyMethodName("translateKeyDown");

        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }

        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() - 1);
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length() - 1);
            }
        }

        onKey(c, null);

        return true;
    }

    /**
     * Use this to monitor key events being delivered to the application. We get
     * first crack at them, and can either resume them or let them continue to
     * the app.
     */
    // this flag for hardCode Back Press handling purpose(setViewShown False of
    // UpdateCandidate)
    boolean removeUpdatePressOnKeyBack = false;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                notifyMethodName("KEYCODE_BACK");

                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }

                removeUpdatePressOnKeyBack = true;
                // mSuggesations.clear();
                // notifyMethodName(Boolean.toString(removeUpdatePressOnKeyBack));
                break;

            case KeyEvent.KEYCODE_DEL:

                notifyMethodName("KEYCODE_DEL");

                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                notifyMethodName("KEYCODE_ENTER");

                // Toast.makeText(getApplicationContext(), "onKeyDown Im Enter",
                // 1000).show();
                return false;

            default:
                notifyMethodName("onKeyDown default");

                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE && (event.getMetaState() & KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application. We get
     * first crack at them, and can either resume them or let them continue to
     * the app.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        notifyMethodName("onKeyUp");

        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState, keyCode, event);
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        notifyMethodName("commiteTyep");
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            //mUpdateCandidateData.setLength(0);
            //updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        notifyMethodName("updateShiftKeyState");

        if (attr != null && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        notifyMethodName("isAlphabet");

        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        notifyMethodName("keyDownUp");

        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        notifyMethodName("sendKey");
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    public void onKey(int primaryCode, int[] keyCodes) {
        notifyMethodName("OnKey");
        if (isWordSeparator(primaryCode)) {
            if (mComposing.length() > 0) {
                getCurrentInputConnection().commitText(dataForCommitInputCurrection.toString(), 1);
            }
            sendKey(primaryCode);
            dataForCommitInputCurrection.setLength(0);
            dataForonUpdateSelection.setLength(0);
            mComposing.setLength(0);
            updateCandidates();
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();



            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                current = mQwertyKeyboard;
            } else {
                current = mSymbolsKeyboard;

                if (mComposing.length() > 0) {
                    getCurrentInputConnection().commitText(dataForCommitInputCurrection.toString(), 1);
                    dataForCommitInputCurrection.setLength(0);
                }

            }
            mInputView.setKeyboard(current);
            if (current == mSymbolsKeyboard) {
                current.setShifted(false);
            }

        }else if(primaryCode == -10){
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
    }

    /**
     * onText Method is getCurrentInputConnection() and tell the IME to commit
     * the txt
     */
    public void onText(CharSequence text) {
        notifyMethodName("onText");

        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing text.
     * This will need to be filled in by however you are determining candidates.
     */
    ArrayList<String> mSuggesations = new ArrayList<String>();
    ArrayList<String> mTempSugg = new ArrayList<String>();

    int flagForAddToDic = 0;
    String listFromSaveDictFile[];
    StringBuffer getToDict;

    private void updateCandidates() {
        notifyMethodName("updatecandidte");
        saveUserData();
        if (!mCompletionOn) {
            mSuggesations.clear();
            if ((mComposing.length() > 0 || dataForonUpdateSelection.length() > 0)
                    && !removeUpdatePressOnKeyBack && !flagForOnOffDictInPassField && !flagForTypeVariationWebEditText) {
                String str;
                Boolean ifCond;
                if (mComposing.length() > 0) {
                    str = mComposing.toString().toLowerCase();
                    ifCond = Character.isUpperCase(mComposing.charAt(0));
                } else {
                    str = dataForonUpdateSelection.toString().toLowerCase();
                    ifCond = Character.isUpperCase(dataForonUpdateSelection.charAt(0));
                }

                String str2 = null;
                flagForAddToDic = 0;

                for (int i = 0; i < Dicte1.array.length; i++) {
                    String a = Dicte1.array[i][0];
                    if (a.startsWith(str)) {
                        flagForAddToDic = 1;
                        if (mSuggesations.size() < 25) {
                            if (mCapsLock) {
                                str2 = a.toUpperCase();
                            } else if (ifCond) {
                                char[] ch = a.toCharArray();
                                ch[0] = Character.toUpperCase(ch[0]);
                                str2 = new String(ch);
                            } else {
                                str2 = a;
                            }
                            mSuggesations.add(str2);
                        }
                    }

                }
                for (int i = 0; i < listFromSaveDictFile.length; i++) {
                    String a = listFromSaveDictFile[i].toLowerCase();
                    if (a.startsWith(str)) {
                        flagForAddToDic = 1;
                        if (mSuggesations.size() < 25) {
                            if (mCapsLock) {
                                str2 = a.toUpperCase();
                            } else if (ifCond) {
                                char[] ch = a.toCharArray();
                                ch[0] = Character.toUpperCase(ch[0]);
                                str2 = new String(ch);
                            } else {
                                str2 = a;
                            }
                            mSuggesations.add(str2);
                        }
                    }
                }
                if (mSuggesations.size() == 0) {
                    if (mComposing.length() != 0)
                        mSuggesations.add(mComposing.toString());
                    else
                        mSuggesations.add(dataForonUpdateSelection.toString());
                }
                setSuggestions(mSuggesations, true, true);
            } else {
                if (strForAddtoDict.length() != 0 && strForAddtoDict.length() >= 3) {
                    if (flagForAddToDic == 0) {
                        mTempSugg.clear();
                        mTempSugg.add(strForAddtoDict.toString() + " | Touch To Save");
                        setSuggestions(mTempSugg, true, true);
                        flagForAddToDic = 1;
                    }
                } else {
                    mTempSugg.clear();
                    flagForAddToDic = 1;
                    strForAddtoDict = "";
                    setSuggestions(null, false, true);
                }

            }
        }

    }

    private void saveUserData() {
        notifyMethodName("saveUserData");

        try {
            getToDict = new StringBuffer();
            InputStream instream = getApplicationContext().openFileInput("saveDataOfDict.txt");
            if (instream != null) {
                InputStreamReader inputreader = new InputStreamReader(instream);
                BufferedReader buffreader = new BufferedReader(inputreader);
                String line = null;
                while ((line = buffreader.readLine()) != null) {
                    getToDict.append(line);
                }
                instream.close();
            }
        } catch (Exception e) {
        }
        listFromSaveDictFile = StringToken.spliteMethods(getToDict.toString(), ", ");
    }

    public void setSuggestions(List<String> suggestions, boolean completions, boolean typedWordValid) {
         notifyMethodName("setSuggestions");
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }

    private void handleBackspace() {
        notifyMethodName("handleBackspace");

        flagForSpecialCharContainsNewLine = false;
        final int mLength = dataForCommitInputCurrection.length();

        if (mLength >= 1) {
            dataForCommitInputCurrection.delete(mLength - 1, mLength);
            getCurrentInputConnection().setComposingText(dataForCommitInputCurrection, 1);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
            updateCandidates();
        }

        final int length = mComposing.length();

        if (length > 1) {
            mComposing.delete(length - 1, length);
            //mUpdateCandidateData.delete(length - 1, length);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            dataForonUpdateSelection.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
        notifyMethodName("handleShift");

        if (mInputView == null) {
            return;
        }

        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mQwertyKeyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        notifyMethodName("handlecharacter");

        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (flagForTypeVariationWebEditText) {
            //here mUpdateInputEditText is set to Zero because that time i was used in National Enquire
            //EditText Field append prev Character with new one..
            dataForCommitInputCurrection.setLength(0);
        }

        if (isAlphabet(primaryCode) && mPredictionOn) {

            mComposing.append((char) primaryCode);
            dataForCommitInputCurrection.append((char) primaryCode);

            if (flagForonUpdateSelection) {
                mComposing.setLength(0);
                dataForonUpdateSelection.append((char) primaryCode);
                mComposing.append(dataForonUpdateSelection.toString());
                flagForonUpdateSelection = false;
            }

            getCurrentInputConnection().setComposingText(dataForCommitInputCurrection, 1);
            updateCandidates();
            updateShiftKeyState(getCurrentInputEditorInfo());

        } else {

            getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
            updateCandidates();
        }
    }

    private void handleClose() {
        notifyMethodName("handleClose");

        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private void checkToggleCapsLock() {
        notifyMethodName("checkToggleCapsLock");

        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }

    private String getWordSeparators() {
        notifyMethodName("getWordSeparators");

        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        notifyMethodName("isWordSeparator");

        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char) code));
    }

    public void pickDefaultCandidate() {
        notifyMethodName("pickDefaultCandidate");

        pickSuggestionManually(0);
    }

    public void pickSuggestionManually(int index) {
         notifyMethodName("pickSuggesationMenually");

        if (mCompletionOn && mCompletions != null && index >= 0 && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0 || dataForonUpdateSelection.length() > 0) {

            if (!flagForSpecialCharContainsNewLine) {
                if (!getCurrentInputConnection().getTextBeforeCursor(1, 0).equals(" ")) {
                    String textBefore = getCurrentInputConnection().getTextBeforeCursor(25, 0).toString();
                    String[] strTemp = StringToken.spliteMethods(textBefore.replace("\n", " "), "   ");
                    textBefore = strTemp[strTemp.length - 1];
                    int len = textBefore.length();
                    String holdSugg = mSuggesations.get(index) + " ";

                    if (!(textBefore.equals(" "))) {
                        String offData = holdSugg.substring(len, holdSugg.length());
                        getCurrentInputConnection().setComposingText("", 0);
                        getCurrentInputConnection().commitText(dataForCommitInputCurrection.toString() + offData, 1);

                    }

                    dataForCommitInputCurrection.setLength(0);
                    dataForonUpdateSelection.setLength(0);
                    mComposing.setLength(0);

                }
            } else {
                dataForCommitInputCurrection.setLength(0);
                dataForonUpdateSelection.setLength(0);
                mComposing.setLength(0);
                flagForSpecialCharContainsNewLine = false;
                getCurrentInputConnection().commitText(mSuggesations.get(index) + " ",
                        (mSuggesations.get(index) + " ").length() - (mSuggesations.get(index) + " ").length() + 1);

            }
            updateCandidates();

        } else if (strForAddtoDict.length() != 0) {
            try {
                FileOutputStream fOut = openFileOutput("saveData.txt", MODE_APPEND);
                OutputStreamWriter osw = new OutputStreamWriter(fOut);
                osw.write(strForAddtoDict.toLowerCase() + ",");
                osw.flush();
                osw.close();
            } catch (java.io.IOException e) {

            }

            strForAddtoDict = "";
            updateCandidates();

        }
    }

    public void swipeRight() {
        notifyMethodName("swipeRight");

        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }

    public void swipeLeft() {
        notifyMethodName("swipeLeft");

        handleBackspace();
    }

    public void swipeDown() {
        notifyMethodName("");
        handleClose();
    }

    public void swipeUp() {
        notifyMethodName("swipeUp");

    }

    String strForAddtoDict = "";

    public void onPress(int primaryCode) {
        notifyMethodName("onPress");

        if (primaryCode == 32 && flagForAddToDic == 0) {
            strForAddtoDict = mComposing.toString();
        } else {
            strForAddtoDict = "";
        }
    }

    public void onRelease(int primaryCode) {
        notifyMethodName("onRelease");

    }
}
