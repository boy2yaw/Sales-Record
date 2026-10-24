package com.boy2yaw.dsr;

import android.app.DatePickerDialog;
import android.widget.DatePicker;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Environment;
import android.provider.Settings;
import java.util.Properties;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.util.TypedValue;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    // Permanent Google Apps Script Web App endpoint for update information.
    // Update metadata can change on the server without changing this URL in the app.
    private static final String UPDATE_INFO_URL = "https://raw.githubusercontent.com/boy2yaw/SalesRecordUpdate/refs/heads/main/version.json";
    // The APK download URL is read from the remote version.json file.
    private static final String UPDATE_APK_NAME = "dsr-update.apk";
    private boolean updateCheckScheduled = false;
    private boolean updateDialogShown = false;
    // DSR premium teal/charcoal UI; text uses SP and modest width-aware scaling.
    // Modern professional DSR palette
    private int NAVY = 0xff14b8a6;
    private int NAVY_DARK = 0xff0f766e;
    private int PAGE_BG = 0xff181c20;
    private int CARD_BG = 0xff252b31;
    private int FIELD_BG = 0xff303840;
    private int BORDER = 0xff3b454e;
    private int TEXT_MAIN = 0xfff1f5f9;
    private int TEXT_MUTED = 0xffb0bac4;
    private int HEADER_BG = 0xff20262c;
    private boolean lightTheme = false;
    private boolean creditMode = false;
    private EditText partialPayment;
    private SharedPreferences prefs;
    private boolean languageTagalog = false; // English is the default app language.

    private SQLiteDatabase salesDb;
    private boolean showInitialInventoryPrompt = false;
    private boolean initialPromptShownThisSession = false;
    private ArrayList<String> names = new ArrayList<String>();
    private ArrayList<Double> boxes = new ArrayList<Double>();
    private ArrayList<Double> reams = new ArrayList<Double>();
    // Selling price per ream for each product. Box price is automatically 50 × ream price.
    private ArrayList<Double> productPrices = new ArrayList<Double>();
    // Fixed starting quantities for the week; current boxes/reams change with sales and withdrawals.
    private ArrayList<Double> beginningBoxes = new ArrayList<Double>();
    private ArrayList<Double> beginningReams = new ArrayList<Double>();
    private ArrayList<Sale> sales = new ArrayList<Sale>();
    private LinearLayout host, inventoryList, salesList, salesListSummary;
    private Button salesListDateButton;
    private HorizontalScrollView salesHistoryDayScroll;
    private Button[] salesHistoryDayButtons = new Button[7];
    private Calendar salesListSelectedDate = Calendar.getInstance();
    private int collectionCategory = 0; // 0 = Receivable Collection, 1 = Partial
    private int collectionWeekday = 0; // Monday=0 ... Friday=4
    private Calendar collectionSelectedDate = null; // Exact date selected in Collection history
    private View collectionDialogRoot;
    private AlertDialog collectionDialog;
    private Button[] tabButtons;
    private Button cashModeButton, creditModeButton;
    private LinearLayout paymentModeNav;
    private LinearLayout inventoryTotal;
    private EditText invName, invBox, invReam, customer, saleBox, saleReam, saleCash, receivableCollection;
    private AutoCompleteTextView customerAddress;
    private Spinner productSpinner;
    private TextView dateView;
    private Calendar headerDate = Calendar.getInstance();
    private LinearLayout tempOrderList;
    private TextView tempOrderSummary;
    private TextView tempOrderTotalValue;
    private ArrayList<String> tempProducts = new ArrayList<String>();
    private ArrayList<Double> tempBoxes = new ArrayList<Double>();
    private ArrayList<Double> tempReams = new ArrayList<Double>();
    // Cash and Credit must never share an unsaved order draft.
    private static class SalesDraft {
        ArrayList<String> products = new ArrayList<String>();
        ArrayList<Double> boxQty = new ArrayList<Double>();
        ArrayList<Double> reamQty = new ArrayList<Double>();
        String customer = "", address = "", boxInput = "", reamInput = "", cashInput = "0", collectionInput = "0", partialInput = "0";
        int productPosition = 0;
    }
    private final SalesDraft cashDraft = new SalesDraft();
    private final SalesDraft creditDraft = new SalesDraft();
    private boolean cashDraftInitialized = false;
    private boolean creditDraftInitialized = false;
    private int currentTab = 1;
    // Keeps the actual page navigation order for the device Back button.
    private final ArrayList<Integer> tabHistory = new ArrayList<Integer>();
    private boolean navigatingBack = false;
    private float swipeStartX, swipeStartY;
    private boolean swipeCandidate;
    private int animationDirection = 0;
    private ArrayList<String> endingRecords = new ArrayList<String>();
    private ArrayList<String> beginningHistory = new ArrayList<String>();
    private ArrayList<String> withdrawRecords = new ArrayList<String>();
    private LinearLayout stockPageList;
    private LinearLayout stockPageTotal;
    private String beginningDate = "";
    private String endingDate = "";
    // Date that closes the current sales period, set with Ending Inventory > CUT-OFF.
    private String salesCutoffDate = "";
    private String withdrawViewDate = "";
    private static final int REQUEST_OPEN_BACKUP = 4101;
    private static final int REQUEST_SAVE_BACKUP = 4102;
    private static final int REQUEST_EXPORT_CSV = 4103;
    private static final int REQUEST_EXPORT_PDF = 4104;
    private static final int REQUEST_BACKUP_FOLDER = 4105;
    private boolean startupBackupFlowHandled = false;

    private class Sale {
        String date, customer, address, product;
        double box, ream, cash, receivable, unitPrice, partial, balance;
        boolean credit;
        Sale(String d, String c, String p, double b, double r) {
            this(d,c,p,b,r,0,0,1420.0);
        }
        Sale(String d, String c, String p, double b, double r, double cashPaid, double receivableCollected) {
            this(d,c,p,b,r,cashPaid,receivableCollected,1420.0);
        }
        Sale(String d, String c, String p, double b, double r, double cashPaid, double receivableCollected, double pricePerReam) {
            date=d; customer=c; address=""; product=p; box=b; ream=r; cash=cashPaid; receivable=receivableCollected;
            unitPrice=pricePerReam>0?pricePerReam:1420.0; partial=0; balance=Math.max(0,amount()-cashPaid); credit=false;
        }
        double amount() { return box*(unitPrice*50.0)+ream*unitPrice; }
        String show() {
            String result=date+"\nCustomer: "+customer+(address==null||address.trim().length()==0?"":"\nAddress: "+address)+"\nProduct: "+product+
                    "\nBox: "+qtyText(box)+"  |  Ream: "+qtyText(ream);
            // The unified app always includes monetary details.
            result += "\nAmount: "+moneyText(amount());
            if(credit) result += "\nPartial Payment: "+moneyText(partial)+"\nRemaining Balance: "+moneyText(balance);
            if(receivable>0) result += "\nReceivable Collection: "+moneyText(receivable);
            return result;
        }
    }
    // Balanced width-aware scaling: scale dp-based geometry as well as text, while
    // clamping the factor so very small/large screens do not distort the UI excessively.
    // 390dp is the design reference width; existing proportions and layout structure stay intact.
    private float uiScale() {
        float density = getResources().getDisplayMetrics().density;
        if (density <= 0f) return 1f;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
        return Math.max(0.85f, Math.min(1.08f, widthDp / 390f));
    }
    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density * uiScale() + 0.5f);
    }
    private float responsiveText(float baseSp) {
        // Unified medium typography across the entire app: avoid tiny labels and oversized headings.
        // Keep all TextView/Button text using setSp within a readable 14–20sp range.
        float factor = Math.max(0.88f, Math.min(1.08f, uiScale()));
        return Math.max(14f, Math.min(20f, baseSp * factor));
    }
    private void setSp(TextView view, float baseSp) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, responsiveText(baseSp));
    }
    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        if(strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }
    private void styleCard(View v, int color) {
        v.setBackground(rounded(color, 14, BORDER));
    }
    // Keep monetary amounts clean: preserve the readable text color without
    // individual filled backgrounds, so adjacent amounts do not look crowded.
    private void styleAmountValue(TextView value) {
        value.setTextColor(NAVY);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        value.setPadding(0, dp(3), 0, dp(3));
        value.setBackgroundColor(Color.TRANSPARENT);
    }
    // Subtle horizontal separator used to group related content on pages and dialogs.
    private View sectionDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams params = lp(-1, 1);
        params.setMargins(dp(2), dp(8), dp(2), dp(8));
        divider.setLayoutParams(params);
        return divider;
    }
    private LinearLayout col() { LinearLayout x=new LinearLayout(this); x.setOrientation(1); return x; }
    private LinearLayout row() { LinearLayout x=new LinearLayout(this); x.setOrientation(0); return x; }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); }
    // Central UI translation helper. English remains the default; Tagalog is optional.
    private String tx(String s) {
        if (s == null || !languageTagalog) return s;
        String[][] pairs = new String[][] {
            {"Inventory", "Imbentaryo"}, {"Order", "Order"}, {"Sales List", "Talaan ng Benta"},
            {"CASH", "CASH"}, {"CREDIT", "UTANG"}, {"Beginning Inventory", "Panimulang Imbentaryo"},
            {"Ending Inventory", "Panghuling Imbentaryo"}, {"Withdrawal Inventory", "Kinuha sa Imbentaryo"},
            {"DISPOSAL", "BENTA"}, {"Weekly Sales", "Benta Kada Linggo"}, {"Monthly Sales", "Benta Kada Buwan"},
            {"TOTAL SALES", "KABUUANG BENTA"}, {"CUSTOMER NAME", "PANGALAN NG CUSTOMER"},
            {"CUSTOMER ADDRESS", "ADDRESS NG CUSTOMER"}, {"Enter customer name", "Ilagay ang pangalan ng customer"},
            {"Enter address", "Ilagay ang address"}, {"PRODUCT", "PRODUKTO"}, {"QUANTITY", "DAMi"},
            {"BOX", "KAHON"}, {"REAM", "REAM"}, {"ADD ORDER", "MAGDAGDAG NG ORDER"},
            {"＋  ADD ORDER", "＋  MAGDAGDAG NG ORDER"}, {"SAVE ORDER", "I-SAVE ANG ORDER"},
            {"SAVE", "I-SAVE"}, {"CANCEL", "KANSELAHIN"}, {"CLEAR", "I-CLEAR"},
            {"CUT-OFF", "CUT-OFF"}, {"Search", "Maghanap"}, {"Search customer", "Maghanap ng customer"},
            {"Date", "Petsa"}, {"Beginning date", "Petsa ng simula"}, {"Ending date", "Petsa ng pagtatapos"},
            {"Withdrawal date", "Petsa ng pagkuha"}, {"Add Product", "Magdagdag ng Produkto"},
            {"Select product", "Pumili ng produkto"}, {"— Select product —", "— Pumili ng produkto —"},
            {"Add a product in Inventory tab", "Magdagdag ng produkto sa Imbentaryo"},
            {"Customer order saved", "Na-save ang order ng customer"}, {"Receivable collection saved", "Na-save ang bayad sa utang"},
            {"SAVE", "I-SAVE"}, {"EDIT", "I-EDIT"}, {"DELETE", "BURAHIN"}, {"BACK", "BUMALIK"},
            {"No products added yet", "Wala pang naidaragdag na produkto"},
            {"English", "English"}, {"Tagalog", "Tagalog"}
        };
        String out = s;
        for (int i=0; i<pairs.length; i++) {
            if (out.equals(pairs[i][0])) return pairs[i][1];
        }
        // Also translate common labels embedded in multi-line tab labels and prompts.
        String[][] fragments = new String[][] {
            {"Inventory", "Imbentaryo"}, {"Sales List", "Talaan ng Benta"},
            {"Weekly Sales", "Benta Kada Linggo"}, {"Monthly Sales", "Benta Kada Buwan"},
            {"Customer Name", "Pangalan ng Customer"}, {"Customer Address", "Address ng Customer"},
            {"Beginning Inventory", "Panimulang Imbentaryo"}, {"Ending Inventory", "Panghuling Imbentaryo"},
            {"Withdrawal Inventory", "Kinuha sa Imbentaryo"}, {"ADD ORDER", "MAGDAGDAG NG ORDER"},
            {"SAVE ORDER", "I-SAVE ANG ORDER"}, {"TOTAL SALES", "KABUUANG BENTA"},
            {"Enter customer name", "Ilagay ang pangalan ng customer"}, {"Enter address", "Ilagay ang address"},
            {"(tap to change)", "(pindutin para baguhin)"}, {"Select product", "Pumili ng produkto"}
        };
        for (int i=0; i<fragments.length; i++) out = out.replace(fragments[i][0], fragments[i][1]);
        return out;
    }

    private TextView label(String s,int size,boolean bold) {
        TextView t=new TextView(this); t.setText(tx(s)); setSp(t,size); t.setTextColor(TEXT_MAIN);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(dp(5),dp(5),dp(5),dp(5)); return t;
    }
    private EditText edit(String hint,boolean numeric) {
        EditText e=new EditText(this); e.setTextColor(TEXT_MAIN); e.setHintTextColor(TEXT_MUTED);
        e.setHint(tx(hint)); e.setSingleLine(true); setSp(e,16); e.setPadding(dp(8),0,dp(8),0);
        e.setBackground(rounded(FIELD_BG, 10, BORDER)); e.setTextColor(TEXT_MAIN); e.setHintTextColor(TEXT_MUTED);
        // Make the active input unmistakable in both themes: a strong teal outline
        // appears while the user is typing, then returns to the normal border.
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                view.setBackground(rounded(FIELD_BG, 10, hasFocus ? NAVY : BORDER));
                if (view instanceof EditText) {
                    ((EditText)view).setTextColor(TEXT_MAIN);
                    ((EditText)view).setHintTextColor(TEXT_MUTED);
                }
            }
        });
        if(numeric) {
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            // For every Box/Ream quantity field throughout the app, select the current
            // value on focus so typing a new quantity replaces the default/current value.
            e.setSelectAllOnFocus(true);
        }
        return e;
    }
    // Lightweight press feedback for every button in the app.
    private void addPressAnimation(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent event) {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f)
                        .setDuration(75).start();
                } else if (action == android.view.MotionEvent.ACTION_UP ||
                           action == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(110).start();
                }
                return false; // Keep normal click and long-click behavior.
            }
        });
    }

    private Button btn(String s) {
        Button b=new Button(this); b.setText(tx(s)); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        setSp(b,14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackground(rounded(NAVY_DARK, 12, 0));
        b.setPadding(dp(12),dp(6),dp(12),dp(6));
        if(android.os.Build.VERSION.SDK_INT>=21) b.setElevation(dp(1));
        addPressAnimation(b);
        return b;
    }

    // Consistent geometry for all four Sales action buttons.
    private void styleSalesActionButton(Button button) {
        button.setBackground(rounded(NAVY_DARK, 12, 0));
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4),0,dp(4),0);
        button.setIncludeFontPadding(false);
        setSp(button, 14);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    }

    // Shared sizing/alignment for the inventory and sales navigation/action buttons.
    private void styleUniformActionButton(Button button) {
        button.setBackground(rounded(NAVY_DARK, 12, 0));
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setIncludeFontPadding(false);
        setSp(button, 14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    // Makes every standard alert dialog follow the selected app theme.
    private class ThemedDialogBuilder extends AlertDialog.Builder {
        ThemedDialogBuilder() {
            // Choose a matching native dialog theme too; changing only the window
            // background leaves Android's inner dialog panel/buttons in dark mode.
            super(MainActivity.this, lightTheme
                    ? android.R.style.Theme_Material_Light_Dialog_Alert
                    : android.R.style.Theme_Material_Dialog_Alert);
        }
        @Override public AlertDialog show() {
            AlertDialog dialog = super.show();
            applyDialogTheme(dialog);
            return dialog;
        }
    }

    private void applyDialogTheme(AlertDialog dialog) {
        if (dialog == null) return;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : (TextView) dialog.findViewById(titleId);
        if (title != null) title.setTextColor(NAVY);
        TextView message = (TextView) dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(TEXT_MAIN);
        ListView list = dialog.getListView();
        if (list != null) {
            list.setBackgroundColor(CARD_BG);
            list.setDivider(new ColorDrawable(BORDER));
            list.setDividerHeight(dp(1));
            list.setCacheColorHint(CARD_BG);
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (child instanceof TextView) ((TextView) child).setTextColor(TEXT_MAIN);
            }
        }
        int[] buttons = new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL};
        for (int i = 0; i < buttons.length; i++) {
            Button button = dialog.getButton(buttons[i]);
            if (button != null) button.setTextColor(TEXT_MAIN);
        }
    }

    private void setDatePickerTextColors(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setDatePickerTextColors(group.getChildAt(i), color);
            }
        }
    }

    // Compact spinner-style date picker used only by the Daily Sales report.
    // The calendar grid plus spinners together made this picker unnecessarily tall.
    private void showCompactDailySalesDatePicker(DatePickerDialog dialog) {
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                DatePickerDialog picker = (DatePickerDialog) d;
                final android.view.Window pickerWindow = picker.getWindow();
                final int pickerWidth = Math.min(getResources().getDisplayMetrics().widthPixels - dp(48), dp(324));
                if (pickerWindow != null) {
                    pickerWindow.setBackgroundDrawable(new ColorDrawable(CARD_BG));
                    pickerWindow.setLayout(pickerWidth, WindowManager.LayoutParams.WRAP_CONTENT);
                    pickerWindow.setGravity(Gravity.CENTER);
                }
                Button ok = picker.getButton(DatePickerDialog.BUTTON_POSITIVE);
                Button cancel = picker.getButton(DatePickerDialog.BUTTON_NEGATIVE);
                if (ok != null) ok.setTextColor(NAVY);
                if (cancel != null) cancel.setTextColor(TEXT_MAIN);
                DatePicker datePicker = picker.getDatePicker();
                if (datePicker != null) {
                    datePicker.setBackgroundColor(CARD_BG);
                    datePicker.setCalendarViewShown(true);
                    datePicker.setSpinnersShown(false);
                    setDatePickerTextColors(datePicker, TEXT_MAIN);
                }
                int headerId = getResources().getIdentifier("date_picker_header", "id", "android");
                View headerPanel = headerId == 0 ? null : picker.findViewById(headerId);
                if (headerPanel != null) {
                    headerPanel.setBackgroundColor(NAVY_DARK);
                    android.view.ViewGroup.LayoutParams headerLp = headerPanel.getLayoutParams();
                    if (headerLp != null) { headerLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT; headerPanel.setLayoutParams(headerLp); }
                }
                int dateId = getResources().getIdentifier("date_picker_header_date", "id", "android");
                TextView header = dateId == 0 ? null : (TextView) picker.findViewById(dateId);
                if (header != null) header.setTextColor(Color.WHITE);
                int yearId = getResources().getIdentifier("date_picker_year", "id", "android");
                TextView year = yearId == 0 ? null : (TextView) picker.findViewById(yearId);
                if (year != null) year.setTextColor(Color.WHITE);
                if (pickerWindow != null) pickerWindow.getDecorView().post(new Runnable() {
                    public void run() {
                        pickerWindow.setLayout(pickerWidth, WindowManager.LayoutParams.WRAP_CONTENT);
                    }
                });
            }
        });
        dialog.show();
    }

    private void showThemedDatePicker(DatePickerDialog dialog) {
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                DatePickerDialog picker = (DatePickerDialog) d;
                final int dialogBg = CARD_BG;
                final int mainText = TEXT_MAIN;
                final int accent = NAVY_DARK;
                final android.view.Window pickerWindow = picker.getWindow();
                final int pickerWidth = Math.min(getResources().getDisplayMetrics().widthPixels - dp(48), dp(324));
                if (pickerWindow != null) {
                    pickerWindow.setBackgroundDrawable(new ColorDrawable(dialogBg));
                    pickerWindow.setLayout(pickerWidth, WindowManager.LayoutParams.WRAP_CONTENT);
                    pickerWindow.setGravity(Gravity.CENTER);
                }
                Button ok = picker.getButton(DatePickerDialog.BUTTON_POSITIVE);
                Button cancel = picker.getButton(DatePickerDialog.BUTTON_NEGATIVE);
                if (ok != null) ok.setTextColor(accent);
                if (cancel != null) cancel.setTextColor(mainText);
                DatePicker datePicker = picker.getDatePicker();
                if (datePicker != null) {
                    datePicker.setBackgroundColor(dialogBg);
                    datePicker.setCalendarViewShown(true);
                    datePicker.setSpinnersShown(false);
                    // Calendar-only mode is shorter and cleaner than showing both
                    // the calendar grid and redundant date spinners together.
                    setDatePickerTextColors(datePicker, mainText);
                }
                int headerId = getResources().getIdentifier("date_picker_header", "id", "android");
                View headerPanel = headerId == 0 ? null : picker.findViewById(headerId);
                if (headerPanel != null) {
                    headerPanel.setBackgroundColor(NAVY_DARK);
                    android.view.ViewGroup.LayoutParams headerLp = headerPanel.getLayoutParams();
                    if (headerLp != null) { headerLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT; headerPanel.setLayoutParams(headerLp); }
                }
                int headerDateId = getResources().getIdentifier("date_picker_header_date", "id", "android");
                TextView header = headerDateId == 0 ? null : (TextView) picker.findViewById(headerDateId);
                if (header != null) header.setTextColor(Color.WHITE);
                int yearId = getResources().getIdentifier("date_picker_year", "id", "android");
                TextView year = yearId == 0 ? null : (TextView) picker.findViewById(yearId);
                if (year != null) year.setTextColor(Color.WHITE);
                if (pickerWindow != null) {
                    picker.getWindow().getDecorView().post(new Runnable() {
                        public void run() {
                            if (pickerWindow != null) pickerWindow.setLayout(pickerWidth, WindowManager.LayoutParams.WRAP_CONTENT);
                        }
                    });
                }
            }
        });
        dialog.show();
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Sales Point");
        // Remove the Android app title/action bar at the very top to give the app more room.
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs=getSharedPreferences("dsr_three_tabs",MODE_PRIVATE);
        languageTagalog = prefs.getBoolean("languageTagalog", false);
        salesDb=openOrCreateDatabase("SalesRecord.db", MODE_PRIVATE, null);
        salesDb.execSQL("CREATE TABLE IF NOT EXISTS app_state (id INTEGER PRIMARY KEY, data TEXT NOT NULL)");
        load();
        showInitialInventoryPrompt = names.size()==0;
        lightTheme=prefs.getBoolean("lightTheme",false);
        // Sales Point now has one unified feature set (formerly Advanced mode).
        if (b == null) {
            // Show the splash only on a fresh launch, not after an orientation recreation.
            currentTab=1;
            tabHistory.clear();
        } else {
            currentTab=b.getInt("restoredCurrentTab", 1);
            ArrayList<Integer> restoredHistory=b.getIntegerArrayList("restoredTabHistory");
            tabHistory.clear();
            if (restoredHistory != null) tabHistory.addAll(restoredHistory);
        }
        applyThemePalette();
        if (b == null) {
            showCinematicSplash();
        } else {
            // Rebuild the current page immediately; do not replay the loading screen.
            makeShell();
            showTab(currentTab);
        }
        scheduleUpdateCheck();
        scheduleBackgroundUpdateChecks();
        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 7104);
        }
    }

    private void scheduleBackgroundUpdateChecks() {
        try {
            android.content.Intent intent = new android.content.Intent(this, UpdateCheckReceiver.class);
            intent.setAction(UpdateCheckReceiver.ACTION_CHECK_UPDATES);
            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(this, 7105, intent, flags);
            android.app.AlarmManager alarm = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm != null) {
                long firstCheck = System.currentTimeMillis() + 15L * 60L * 1000L;
                alarm.setInexactRepeating(android.app.AlarmManager.RTC_WAKEUP, firstCheck,
                        6L * 60L * 60L * 1000L, pending);
            }
        } catch (Exception ignored) { }
    }

    private void scheduleUpdateCheck() {
        if (updateCheckScheduled) return;
        updateCheckScheduled = true;
        new android.os.Handler().postDelayed(new Runnable() {
            @Override public void run() {
                checkForUpdates();
            }
        }, 15000L);
    }

    private void checkForUpdates() { checkForUpdates(false); }

    private void checkForUpdates(final boolean manual) {
        if (UPDATE_INFO_URL == null || UPDATE_INFO_URL.length() == 0) {
            if (manual) new AlertDialog.Builder(this).setTitle(tx("Check Update")).setMessage(tx("Update checker is not configured yet.")).setPositiveButton(tx("OK"), null).show();
            return;
        }
        if (manual) Toast.makeText(this,tx("Checking for updates..."), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(UPDATE_INFO_URL);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setInstanceFollowRedirects(true);
                    int status = connection.getResponseCode();
                    if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + status);
                    StringBuilder body = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    reader.close();
                    org.json.JSONObject info = new org.json.JSONObject(body.toString());
                    final int remoteCode = info.optInt("versionCode", 0);
                    final String remoteName = info.optString("versionName", "");
                    final String notes = info.optString("releaseNotes", "Bug fixes and improvements.");
                    final String apkUrl = info.optString("apkUrl", "").trim();
                    final int currentCode = getInstalledVersionCode();
                    if (remoteCode > currentCode && remoteName.length() > 0 && apkUrl.startsWith("https://")) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (isFinishing() || updateDialogShown) return;
                                updateDialogShown = true;
                                String updateMessage = "Installed version: " + getInstalledVersionName()
                                    + " (" + currentCode + ")"
                                    + "\n\nNew version to update: " + remoteName
                                    + " (" + remoteCode + ")"
                                    + "\n\nRelease notes:\n" + notes
                                    + "\n\nDownload and install this update now?";
                                new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(tx("New Version Available!"))
                                    .setMessage("Version: " + remoteName + "\n\nRelease notes:\n" + notes
                                            + "\n\nWould you like to update Sales Point now?")
                                    .setNegativeButton(tx("Later"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { d.dismiss(); updateDialogShown = false; } })
                                    .setPositiveButton(tx("Update & Install"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { updateDialogShown = false; downloadUpdateApk(apkUrl); } }).show();
                            }
                        });
                    } else if (manual) {
                        final boolean invalid = remoteCode <= 0 || remoteName.length() == 0 || !apkUrl.startsWith("https://");
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (isFinishing()) return;
                                String message = invalid ? "Hindi mabasa nang tama ang update information. Tingnan ang version.json at GitHub repository/release access." : "Version is up to date\nInstalled version " + getInstalledVersionName() + " (" + currentCode + ")";
                                new AlertDialog.Builder(MainActivity.this).setTitle(tx("Check Update")).setMessage(message).setPositiveButton(tx("OK"), null).show();
                            }
                        });
                    }
                } catch (final Exception error) {
                    if (manual) runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            new AlertDialog.Builder(MainActivity.this).setTitle(tx("Check Update Failed"))
                                .setMessage("Hindi makuha ang update information. Suriin ang internet connection at siguraduhing public ang version.json sa GitHub.\n\n" + error.getMessage())
                                .setPositiveButton(tx("OK"), null).show();
                        }
                    });
                } finally { if (connection != null) connection.disconnect(); }
            }
        }).start();
    }

    private int getInstalledVersionCode() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; }
        catch (Exception e) { return 0; }
    }

    private String getInstalledVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "Unknown"; }
    }

    // Accept GitHub Release asset URLs (and legacy Google Drive links).
    // The GitHub repository and release asset must be publicly accessible.
    private String normalizeGoogleDriveApkUrl(String rawUrl) throws IOException {
        if (rawUrl == null) throw new IOException("Missing apkUrl in version.json");
        String value = rawUrl.trim();
        if (value.length() == 0) throw new IOException("Missing apkUrl in version.json");

        String fileId = null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:drive\\.google\\.com/file/d/|drive\\.google\\.com/uc\\?(?:[^#]*&)?id=)([A-Za-z0-9_-]+)")
                .matcher(value);
        if (matcher.find()) fileId = matcher.group(1);

        if (fileId != null) {
            return "https://drive.google.com/uc?export=download&id=" + fileId;
        }
        if (!value.startsWith("https://")) {
            throw new IOException("The APK link in version.json must start with https://");
        }
        return value;
    }

    private void downloadUpdateApk(final String apkUrl) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                FileOutputStream output = null;
                InputStream input = null;
                File apk = null;
                try {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new IOException("Download folder unavailable");
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create download folder");

                    apk = new File(dir, UPDATE_APK_NAME);
                    URL url = new URL(normalizeGoogleDriveApkUrl(apkUrl));
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(60000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                    connection.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");

                    int status = connection.getResponseCode();
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw new IOException("GitHub/APK server returned HTTP " + status
                                + ". Check that the APK link is correct and publicly accessible.");
                    }

                    String contentType = connection.getContentType();
                    input = connection.getInputStream();
                    output = new FileOutputStream(apk);
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    input.close();
                    input = null;
                    output.flush();
                    output.close();
                    output = null;
                    connection.disconnect();
                    connection = null;

                    if (apk.length() < 10000L) {
                        String type = contentType == null ? "unknown" : contentType;
                        apk.delete();
                        apk = null;
                        throw new IOException("APK server did not return the APK (received "
                                + type + ", " + (apk == null ? "a small file" : apk.length() + " bytes")
                                + "). Make sure apkUrl in version.json links to the APK file itself, not version.json or a Drive preview page.");
                    }

                    FileInputStream check = new FileInputStream(apk);
                    int b1 = check.read();
                    int b2 = check.read();
                    check.close();
                    if (b1 != 0x50 || b2 != 0x4b) {
                        apk.delete();
                        apk = null;
                        throw new IOException("The downloaded file is not an APK. The server may have returned an HTML page instead of the APK. Check that apkUrl points directly to the APK asset in a public GitHub Release.");
                    }

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle(tx("Update downloaded"))
                                .setMessage(tx("The update is ready. Android will ask you to confirm installation. Your sales database is kept by the app."))
                                .setNegativeButton(tx("Later"), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) { d.dismiss(); }
                                })
                                .setPositiveButton(tx("Install"), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) { launchUpdateInstaller(); }
                                }).show();
                        }
                    });
                } catch (final Exception e) {
                    if (apk != null && apk.exists()) apk.delete();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle(tx("Update download failed"))
                                .setMessage("Could not download a valid APK.\n\n" + e.getMessage()
                                        + "\n\nIn version.json, apkUrl must point to the APK file itself—not the version.json file.")
                                .setPositiveButton(tx("OK"), null).show();
                        }
                    });
                } finally {
                    if (input != null) try { input.close(); } catch (Exception ignored) { }
                    if (output != null) try { output.close(); } catch (Exception ignored) { }
                    if (connection != null) connection.disconnect();
                }
            }
        }).start();
    }

        private void launchUpdateInstaller() {
        if (android.os.Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            getSharedPreferences("dsr_three_tabs", MODE_PRIVATE).edit()
                .putBoolean("pendingUpdateInstall", true).apply();
            new AlertDialog.Builder(this)
                .setTitle(tx("Allow app installation"))
                .setMessage(tx("Android needs permission to install updates downloaded by Sales Point. On the next screen, allow “Install unknown apps”, then return here."))
                .setNegativeButton(tx("Cancel"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        getSharedPreferences("dsr_three_tabs", MODE_PRIVATE).edit()
                            .putBoolean("pendingUpdateInstall", false).apply();
                    }
                })
                .setPositiveButton(tx("Open Settings"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this,tx("Open Android Settings and allow this app to install unknown apps."), Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
            return;
        }
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File apk = new File(dir, UPDATE_APK_NAME);
            if (!apk.exists()) throw new IOException("Update APK not found");
            Uri uri = Uri.parse("content://" + getPackageName() + ".updateprovider/" + UPDATE_APK_NAME);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this,tx("Could not open the Android installer: ") + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 26 && prefs != null
                && prefs.getBoolean("pendingUpdateInstall", false)
                && getPackageManager().canRequestPackageInstalls()) {
            prefs.edit().putBoolean("pendingUpdateInstall", false).apply();
            new android.os.Handler().postDelayed(new Runnable() {
                @Override public void run() { launchUpdateInstaller(); }
            }, 400L);
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("restoredCurrentTab", currentTab);
        outState.putIntegerArrayList("restoredTabHistory", new ArrayList<Integer>(tabHistory));
        super.onSaveInstanceState(outState);
    }

    // Show cinematic splash with a real animated progress bar for 1.5 seconds.
    private void showCinematicSplash() {
        final int splashDuration = 1500;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout splashRoot = new FrameLayout(this);
        splashRoot.setBackgroundColor(Color.BLACK);

        ImageView splash = new ImageView(this);
        splash.setImageResource(R.drawable.splash_loading);
        splash.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splash.setBackgroundColor(Color.BLACK);
        splashRoot.addView(splash, new FrameLayout.LayoutParams(-1, -1));

        // Real progress indicator over the artwork, anchored at the bottom.
        LinearLayout progressPanel = new LinearLayout(this);
        progressPanel.setOrientation(LinearLayout.VERTICAL);
        progressPanel.setGravity(Gravity.CENTER);
        progressPanel.setPadding(dp(28), dp(12), dp(28), dp(18));
        progressPanel.setBackgroundColor(0x99000608);

        TextView loadingLabel = new TextView(this);
        loadingLabel.setText(tx("LOADING  0%"));
        loadingLabel.setTextColor(Color.WHITE);
        setSp(loadingLabel, 14);
        loadingLabel.setGravity(Gravity.CENTER);
        loadingLabel.setLetterSpacing(0.12f);
        progressPanel.addView(loadingLabel, new LinearLayout.LayoutParams(-1, dp(28)));

        final ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff00f060));
            progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff52605a));
        }
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(8));
        progressLp.setMargins(0, dp(4), 0, dp(2));
        progressPanel.addView(progressBar, progressLp);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        splashRoot.addView(progressPanel, panelLp);
        setContentView(splashRoot);

        final android.os.Handler handler = new android.os.Handler();
        final long startedAt = android.os.SystemClock.uptimeMillis();
        final Runnable[] updater = new Runnable[1];
        updater[0] = new Runnable() {
            @Override public void run() {
                long elapsed = android.os.SystemClock.uptimeMillis() - startedAt;
                int progress = (int)Math.min(1000L, elapsed * 1000L / splashDuration);
                progressBar.setProgress(progress);
                loadingLabel.setText(tx("LOADING  ") + (progress / 10) + "%");
                if (progress < 1000) {
                    handler.postDelayed(this, 16L);
                } else {
                    getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    makeShell();
                    showTab(currentTab);
                    beginStartupBackupFlow();
                }
            }
        };
        handler.post(updater[0]);
    }
    @Override protected void onPause() {
        if(prefs!=null) save();
        super.onPause();
    }
    @Override public void onBackPressed() {
        // Back navigates between pages without switching modes.
        // Sales (tab 1) is the starting page.
        // Return to the page the user visited immediately before this one.
        // A same-tab refresh does not add history, so Back won't loop through duplicates.
        if(!tabHistory.isEmpty()) {
            int previous = tabHistory.remove(tabHistory.size()-1);
            animationDirection = previous < currentTab ? -1 : 1;
            navigatingBack = true;
            showTab(previous);
            navigatingBack = false;
            return;
        }
        // Daily Sales is the starting page. If there is no history, go there before exiting.
        if(currentTab != 1) {
            animationDirection = currentTab > 1 ? -1 : 1;
            navigatingBack = true;
            showTab(1);
            navigatingBack = false;
            return;
        }
        new ThemedDialogBuilder()
            .setTitle(tx("Exit app?"))
            .setMessage(tx("Do you want to exit? Your saved work will be kept."))
            .setNegativeButton(tx("CANCEL"), new DialogInterface.OnClickListener(){ public void onClick(DialogInterface d,int w){ d.dismiss(); }})
            .setPositiveButton(tx("EXIT"), new DialogInterface.OnClickListener(){ public void onClick(DialogInterface d,int w){ save(); finish(); }})
            .show();
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        int action=ev.getActionMasked();
        if(action==android.view.MotionEvent.ACTION_DOWN) {
            swipeStartX=ev.getX(); swipeStartY=ev.getY(); swipeCandidate=true;
            // The weekly day selector owns horizontal gestures. Do not let the
            // activity-level tab swipe handler change tabs while scrolling it.
            if(currentTab==2 && salesHistoryDayScroll!=null) {
                android.graphics.Rect dayBounds = new android.graphics.Rect();
                if(salesHistoryDayScroll.getGlobalVisibleRect(dayBounds)
                        && dayBounds.contains((int)ev.getRawX(), (int)ev.getRawY())) {
                    swipeCandidate=false;
                }
            }
        } else if(action==android.view.MotionEvent.ACTION_MOVE && swipeCandidate) {
            float dx=ev.getX()-swipeStartX, dy=ev.getY()-swipeStartY;
            if(Math.abs(dy)>dp(35) && Math.abs(dy)>Math.abs(dx)) swipeCandidate=false;
        } else if(action==android.view.MotionEvent.ACTION_UP && swipeCandidate) {
            float dx=ev.getX()-swipeStartX, dy=ev.getY()-swipeStartY;
            if(Math.abs(dx)>dp(75) && Math.abs(dx)>Math.abs(dy)*1.35f
                    && currentTab>=0 && currentTab<=2) {
                int next=currentTab;
                if(dx<0 && currentTab<2) next=currentTab+1;
                else if(dx>0 && currentTab>0) next=currentTab-1;
                if(next!=currentTab) { animationDirection = dx<0 ? 1 : -1; showTab(next); swipeCandidate=false; return true; }
            }
            swipeCandidate=false;
        } else if(action==android.view.MotionEvent.ACTION_CANCEL) swipeCandidate=false;
        return super.dispatchTouchEvent(ev);
    }

    // Main application data is stored in SQLite (SalesRecord.db), not SharedPreferences.
    // The existing .backup format remains compatible for manual export/import.
    private void load() {
        beginningDate=today(); endingDate=today(); withdrawViewDate=today();
        names.clear(); boxes.clear(); reams.clear(); productPrices.clear();
        beginningBoxes.clear(); beginningReams.clear(); sales.clear();
        endingRecords.clear(); withdrawRecords.clear(); tempProducts.clear(); tempBoxes.clear(); tempReams.clear();
        if (salesDb == null) return;
        Cursor c=null;
        try {
            c=salesDb.rawQuery("SELECT data FROM app_state WHERE id=1", null);
            if(c.moveToFirst()) {
                Properties state=new Properties();
                state.load(new java.io.StringReader(c.getString(0)));
                importBackupProperties(state);
            }
        } catch(Exception ex) {
            // A missing or invalid state is treated as a clean first launch.
            names.clear(); boxes.clear(); reams.clear(); productPrices.clear();
            beginningBoxes.clear(); beginningReams.clear(); sales.clear();
            endingRecords.clear(); withdrawRecords.clear(); tempProducts.clear(); tempBoxes.clear(); tempReams.clear();
        } finally { if(c!=null)c.close(); }
    }
    private void save() {
        if(salesDb==null)return;
        try {
            Properties state=backupProperties();
            java.io.StringWriter writer=new java.io.StringWriter();
            state.store(writer, "Sales Point local database state");
            android.content.ContentValues values=new android.content.ContentValues();
            values.put("id",1); values.put("data",writer.toString());
            salesDb.insertWithOnConflict("app_state",null,values,SQLiteDatabase.CONFLICT_REPLACE);
        } catch(Exception ignored) { }
        // UI preferences and unfinished form drafts remain preferences, not business records.
        SharedPreferences.Editor e=prefs.edit();
        e.putBoolean("lightTheme",lightTheme);
        if(customer!=null)e.putString("draftCustomer",customer.getText().toString());
        if(customerAddress!=null){e.putString("draftAddress",customerAddress.getText().toString());e.putString(creditMode ? "creditDraftAddress" : "cashDraftAddress",customerAddress.getText().toString());}
        if(saleBox!=null)e.putString("draftSaleBox",saleBox.getText().toString());
        if(saleReam!=null)e.putString("draftSaleReam",saleReam.getText().toString());
        if(saleCash!=null)e.putString("draftSaleCash",saleCash.getText().toString());
        if(receivableCollection!=null)e.putString("draftReceivable",receivableCollection.getText().toString());
        if(partialPayment!=null)e.putString("draftPartialPayment",partialPayment.getText().toString());
        if(productSpinner!=null)e.putInt("draftProduct",productSpinner.getSelectedItemPosition());
        e.apply();
        // Keep a portable backup in the user-selected Documents folder when configured.
        writeAutomaticBackup();
    }

    private Uri getBackupTreeUri() {
        try {
            String saved=prefs.getString("backupTreeUri", "");
            return saved.length()==0 ? null : Uri.parse(saved);
        } catch(Exception e) { return null; }
    }

    private void beginStartupBackupFlow() {
        if(startupBackupFlowHandled) return;
        startupBackupFlowHandled=true;
        // Ask about backup restoration/setup once per installed app version.
        // Reopening the app will not show the prompt again; installing an update
        // can show it once for the new version.
        int appVersion = 1;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode() > Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : (int)getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            } else {
                appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            }
        } catch (Exception ignored) { }
        int promptedVersion = prefs.getInt("backupPromptedVersion", -1);
        if (promptedVersion == appVersion) {
            continueStartupWithoutRestore();
            return;
        }
        prefs.edit().putInt("backupPromptedVersion", appVersion).apply();
        Uri tree=getBackupTreeUri();
        if(tree==null) {
            new ThemedDialogBuilder()
                .setTitle(tx("Set Up Local Backup"))
                .setMessage(tx("Sales Point will reuse an existing “Sales Record” backup folder when it finds one, or use/create “SalesPoint” inside Documents. Your database stays inside the app, and a backup copy will be saved in the selected folder."))
                .setPositiveButton(tx("Choose Folder"), new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface d,int w){
                        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        try { startActivityForResult(i,REQUEST_BACKUP_FOLDER); }
                        catch(Exception e){ continueStartupWithoutRestore(); }
                    }
                })
                .setNegativeButton(tx("Not Now"), new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface d,int w){ continueStartupWithoutRestore(); }
                }).show();
            return;
        }
        // Migrate the saved backup location when the previous app used a
        // "Sales Record" folder. The persisted tree URI still retains the
        // original Documents tree ID, so we can safely look for the legacy
        // sibling folder and reuse it when it contains a known backup.
        try {
            Uri compatibleFolder = ensureSalesPointFolder(tree);
            if (compatibleFolder != null) {
                tree = compatibleFolder;
                prefs.edit().putString("backupTreeUri", tree.toString()).apply();
            }
        } catch (Exception ignored) { }
        checkForAutomaticBackup(tree);
    }

    private void continueStartupWithoutRestore() {
        if(showInitialInventoryPrompt) showInitialInventorySetupPrompt();
    }

    // Create/use a dedicated SalesPoint folder inside the folder the user grants.
    // The app stores a URI to this child folder so backups stay organized.
    private Uri ensureSalesPointFolder(Uri selectedTree) throws IOException {
        Cursor c = null;
        Uri legacyFolder = null;
        Uri salesPointFolder = null;
        try {
            // Inspect the root of the granted tree, not just the currently
            // selected child URI. This lets an updated install discover the
            // old "Sales Record" sibling folder even when preferences point
            // at the newer "SalesPoint" folder.
            String rootId = DocumentsContract.getTreeDocumentId(selectedTree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(selectedTree, rootId);
            c = getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE},
                    null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String name = c.getString(1);
                    String mime = c.getString(2);
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue;
                    Uri folder = DocumentsContract.buildDocumentUriUsingTree(selectedTree, c.getString(0));
                    if ("SalesPoint".equalsIgnoreCase(name)) {
                        salesPointFolder = folder;
                    } else if ("Sales Record".equalsIgnoreCase(name)
                            || "SalesRecord".equalsIgnoreCase(name)) {
                        legacyFolder = folder;
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Could not inspect the selected folder", e);
        } finally {
            if (c != null) c.close();
        }

        // Prefer the old folder only when it actually contains a recognized
        // backup; otherwise keep using an existing SalesPoint folder.
        if (legacyFolder != null && findAutomaticBackup(legacyFolder) != null) {
            return legacyFolder;
        }
        if (salesPointFolder != null) return salesPointFolder;
        if (legacyFolder != null) return legacyFolder;

        try {
            String rootId = DocumentsContract.getTreeDocumentId(selectedTree);
            Uri created = DocumentsContract.createDocument(getContentResolver(),
                    DocumentsContract.buildDocumentUriUsingTree(selectedTree, rootId),
                    DocumentsContract.Document.MIME_TYPE_DIR, "SalesPoint");
            if (created == null) throw new IOException("Folder creation was cancelled");
            return DocumentsContract.buildDocumentUriUsingTree(selectedTree,
                    DocumentsContract.getDocumentId(created));
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException("Could not create the SalesPoint folder", e);
        }
    }

    private Uri findAutomaticBackup(Uri tree) {
        Cursor c=null;
        try {
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(tree));
            c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null);
            if(c!=null) while(c.moveToNext()) {
                String name=c.getString(1);
                if("SalesPoint.backup".equalsIgnoreCase(name) || "SalesRecord.backup".equalsIgnoreCase(name)) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));
                }
            }
        } catch(Exception ignored) { }
        finally { if(c!=null)c.close(); }
        return null;
    }

    private void checkForAutomaticBackup(Uri tree) {
        final Uri backup=findAutomaticBackup(tree);
        if(backup==null) { continueStartupWithoutRestore(); return; }
        // Ignore an empty file created before the user entered any real records.
        try {
            InputStream check=getContentResolver().openInputStream(backup);
            Properties existing=new Properties(); existing.load(check); check.close();
            if(propertyInt(existing,"productCount",0)==0 && propertyInt(existing,"saleCount",0)==0
                    && propertyInt(existing,"endingCount",0)==0 && propertyInt(existing,"withdrawCount",0)==0) {
                continueStartupWithoutRestore(); return;
            }
        } catch(Exception ignored) { }
        new ThemedDialogBuilder()
            .setTitle(tx("Restore Saved Data?"))
            .setMessage(tx("A Sales Point backup was found in your selected folder. Do you want to restore it now? Restoring will replace the data currently in this app."))
            .setPositiveButton(tx("Restore Backup"),new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int w){ restoreAutomaticBackup(backup); }
            })
            .setNegativeButton(tx("Start Fresh"),new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int w){ continueStartupWithoutRestore(); }
            }).show();
    }

    private void restoreAutomaticBackup(Uri uri) {
        try {
            InputStream in=getContentResolver().openInputStream(uri);
            Properties p=new Properties(); p.load(in); in.close();
            if(!p.containsKey("version")) throw new IOException("Invalid Sales Point backup file");
            importBackupProperties(p); save(); showTab(currentTab);
            showInitialInventoryPrompt=false;
            Toast.makeText(this,tx("Backup restored successfully"),Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(this,tx("Could not restore backup: ")+e.getMessage(),Toast.LENGTH_LONG).show();
            continueStartupWithoutRestore();
        }
    }

    // Restore entry point used by the app menu. It restores the newest automatically
    // maintained backup without asking the user to browse folders. If no automatic
    // backup exists, the user can use Open to select a backup file.
    private void restoreLatestSavedBackup() {
        Uri tree = getBackupTreeUri();
        Uri backup = tree == null ? null : findAutomaticBackup(tree);
        if (backup == null) {
            new ThemedDialogBuilder()
                .setTitle(tx("No Saved Backup Found"))
                .setMessage(tx("No automatic backup was found in the selected backup folder. You can choose a backup file using Open in the menu."))
                .setPositiveButton(tx("OK"), null)
                .show();
            return;
        }
        new ThemedDialogBuilder()
            .setTitle(tx("Restore Previous Saved Data?"))
            .setMessage(tx("Restore the latest saved backup? This will replace the data currently in the app. Continue only if you want to return to the saved backup."))
            .setNegativeButton(tx("CANCEL"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { d.dismiss(); }
            })
            .setPositiveButton(tx("RESTORE"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { restoreAutomaticBackup(backup); }
            })
            .show();
    }

    private void writeAutomaticBackup() {
        Uri tree=getBackupTreeUri();
        if(tree==null || salesDb==null) return;
        // Do not create a misleading empty backup on first launch. The first real
        // inventory/sales entry will create it automatically.
        if(names.isEmpty() && sales.isEmpty() && endingRecords.isEmpty() && withdrawRecords.isEmpty()) return;
        Uri file=null;
        try {
            Properties p=backupProperties();
            file=findAutomaticBackup(tree);
            if(file!=null) DocumentsContract.deleteDocument(getContentResolver(),file);
            String parentId=DocumentsContract.getDocumentId(tree);
            file=DocumentsContract.createDocument(getContentResolver(),DocumentsContract.buildDocumentUriUsingTree(tree,parentId),"application/octet-stream","SalesPoint.backup");
            if(file==null) return;
            OutputStream out=getContentResolver().openOutputStream(file,"wt");
            if(out==null) return;
            p.store(out,"Sales Point automatic backup"); out.flush(); out.close();
        } catch(Exception ignored) {
            // The database remains saved locally even if removable/document storage fails.
        }
    }

    private void showInitialInventorySetupPrompt() {
        if(initialPromptShownThisSession || !names.isEmpty()) return;
        initialPromptShownThisSession=true;
        new ThemedDialogBuilder()
            .setTitle(tx("Set Up Beginning Inventory"))
            .setMessage(tx("Beginning Inventory is the starting point for your Sales Point calculations. Add your products now, or do it later."))
            .setPositiveButton(tx("Add Beginning Inventory"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Open the Beginning Inventory page and immediately launch its
                    // existing product-name and quantity entry dialog.
                    showTab(5);
                    showStockDialog("BEGINNING INVENTORY");
                }
            })
            .setNegativeButton(tx("Add Later"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { }
            })
            .show();
    }
    private void applyThemePalette() {
        if (lightTheme) {
            NAVY = 0xff0f9f91;
            NAVY_DARK = 0xff087f74;
            PAGE_BG = 0xfff4f7f9;
            CARD_BG = 0xffffffff;
            FIELD_BG = 0xffe5ebef;
            BORDER = 0xffcbd5dc;
            TEXT_MAIN = 0xff17212b;
            TEXT_MUTED = 0xff586775;
            HEADER_BG = 0xffe8eef2;
        } else {
            NAVY = 0xff14b8a6;
            NAVY_DARK = 0xff0f766e;
            PAGE_BG = 0xff181c20;
            CARD_BG = 0xff252b31;
            FIELD_BG = 0xff303840;
            BORDER = 0xff3b454e;
            TEXT_MAIN = 0xfff1f5f9;
            TEXT_MUTED = 0xffb0bac4;
            HEADER_BG = 0xff20262c;
        }
    }

    private void makeShell() {
        LinearLayout root=col();
        root.setBackgroundColor(PAGE_BG);

        // Shared top row: menu on the left and date centered.
        LinearLayout dateRow=row();
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setBackgroundColor(HEADER_BG);
        root.addView(dateRow,lp(-1,46));

        Button menuButton=btn("☰");
        setSp(menuButton, 23);
        menuButton.setTextColor(TEXT_MAIN);
        menuButton.setPadding(dp(8),0,dp(8),0);
        menuButton.setBackgroundColor(Color.TRANSPARENT);
        menuButton.setMinWidth(0);
        menuButton.setMinimumWidth(0);
        menuButton.setElevation(0);
        dateRow.addView(menuButton,lp(54,-1));

        dateView=label("",14,true);
        dateView.setTextColor(NAVY);
        dateView.setGravity(Gravity.CENTER);
        dateRow.addView(dateView,new LinearLayout.LayoutParams(0,dp(38),1));

        // Language selector sits at the upper-right; the compact down-arrow opens choices.
        Button languageButton=btn("⌄");
        setSp(languageButton, 22);
        languageButton.setTextColor(TEXT_MAIN);
        languageButton.setPadding(0,0,0,dp(3));
        languageButton.setBackgroundColor(Color.TRANSPARENT);
        languageButton.setMinWidth(0); languageButton.setMinimumWidth(0);
        languageButton.setElevation(0);
        languageButton.setContentDescription(tx("Language settings"));
        dateRow.addView(languageButton,lp(54,-1));
        languageButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){ showLanguageMenu(v); }
        });

        menuButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){showAppMenu(menuButton);}
        });
        updateHeaderDate();
        dateView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showReadOnlyCalendar(); }
        });
        dateView.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    startX = event.getX(); startY = event.getY(); return true;
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dx = event.getX() - startX, dy = event.getY() - startY;
                    if (Math.abs(dx) > dp(35) && Math.abs(dx) > Math.abs(dy)) {
                        headerDate.add(Calendar.DAY_OF_MONTH, dx < 0 ? 1 : -1);
                        updateHeaderDate();
                    } else {
                        showReadOnlyCalendar();
                    }
                    return true;
                }
                return true;
            }
        });

        // Calculator is accessed from the app menu to preserve vertical space.

        LinearLayout tabs=row();
        tabs.setPadding(dp(2),dp(3),dp(2),dp(3));
        // No enclosing outline; each tab button gets its own subtle outline.
        tabs.setBackgroundColor(HEADER_BG);
        root.addView(tabs,lp(-1,64));

        Button a=btn("▦\nInventory"),b=btn("＋\nOrder"),c=btn("☷\nSales List");
        // Use a more readable tab-label size, with slight screen-width adjustment to keep labels fitting.
        float tabTextSize = 14f;
        setSp(a, tabTextSize);
        setSp(b, tabTextSize);
        setSp(c, tabTextSize);
        a.setGravity(Gravity.CENTER); b.setGravity(Gravity.CENTER); c.setGravity(Gravity.CENTER);
        a.setSingleLine(false); b.setSingleLine(false); c.setSingleLine(false);
        a.setMinHeight(0); b.setMinHeight(0); c.setMinHeight(0);
        a.setPadding(dp(2),dp(2),dp(2),dp(2)); b.setPadding(dp(2),dp(2),dp(2),dp(2));
        c.setPadding(dp(2),dp(2),dp(2),dp(2));
        tabButtons = new Button[]{a,b,c};

        LinearLayout.LayoutParams tabLp1=new LinearLayout.LayoutParams(0,dp(52),1);
        tabLp1.setMargins(dp(3),0,dp(3),0);
        LinearLayout.LayoutParams tabLp2=new LinearLayout.LayoutParams(0,dp(52),1);
        tabLp2.setMargins(dp(3),0,dp(3),0);
        LinearLayout.LayoutParams tabLp3=new LinearLayout.LayoutParams(0,dp(52),1);
        tabLp3.setMargins(dp(3),0,dp(3),0);
        tabs.addView(a,tabLp1);
        tabs.addView(b,tabLp2);
        tabs.addView(c,tabLp3);

        LinearLayout paymentNav=row();
        paymentModeNav=paymentNav;
        paymentNav.setPadding(dp(8),dp(5),dp(8),dp(7));
        paymentNav.setBackgroundColor(HEADER_BG);
        Button cashNav=btn("💵  CASH");
        Button creditNav=btn("▤  CREDIT");
        cashModeButton=cashNav; creditModeButton=creditNav;
        styleUniformActionButton(cashNav); styleUniformActionButton(creditNav);
        setSp(cashNav,13); setSp(creditNav,13);
        LinearLayout.LayoutParams cashNavLp=new LinearLayout.LayoutParams(0,dp(42),1f); cashNavLp.setMargins(0,0,dp(4),0);
        LinearLayout.LayoutParams creditNavLp=new LinearLayout.LayoutParams(0,dp(42),1f); creditNavLp.setMargins(dp(4),0,0,0);
        paymentNav.addView(cashNav,cashNavLp); paymentNav.addView(creditNav,creditNavLp);
        root.addView(paymentNav,lp(-1,-2));
        updatePaymentModeButtons();
        cashNav.setOnClickListener(new View.OnClickListener(){public void onClick(View v){switchSalesPaymentMode(false);}});
        creditNav.setOnClickListener(new View.OnClickListener(){public void onClick(View v){switchSalesPaymentMode(true);}});

        host=col();
        host.setBackgroundColor(PAGE_BG);
        root.addView(host,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);

        a.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTab(0);}});
        b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTab(1);}});
        c.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTab(2);}});
    }

    private SalesDraft getSalesDraft(boolean forCredit) { return forCredit ? creditDraft : cashDraft; }

    private void captureCurrentSalesDraft() {
        SalesDraft d = getSalesDraft(creditMode);
        d.products = new ArrayList<String>(tempProducts);
        d.boxQty = new ArrayList<Double>(tempBoxes);
        d.reamQty = new ArrayList<Double>(tempReams);
        d.customer = customer == null ? prefs.getString("draftCustomer", "") : customer.getText().toString();
        d.address = customerAddress == null ? prefs.getString(creditMode ? "creditDraftAddress" : "cashDraftAddress", "") : customerAddress.getText().toString();
        d.boxInput = saleBox == null ? prefs.getString("draftSaleBox", "") : saleBox.getText().toString();
        d.reamInput = saleReam == null ? prefs.getString("draftSaleReam", "") : saleReam.getText().toString();
        d.cashInput = saleCash == null ? prefs.getString("draftSaleCash", "0") : saleCash.getText().toString();
        d.collectionInput = receivableCollection == null ? prefs.getString("draftReceivable", "0") : receivableCollection.getText().toString();
        d.partialInput = partialPayment == null ? prefs.getString("draftPartialPayment", "0") : partialPayment.getText().toString();
        d.productPosition = productSpinner == null ? prefs.getInt("draftProduct", 0) : productSpinner.getSelectedItemPosition();
        if (creditMode) creditDraftInitialized = true; else cashDraftInitialized = true;
    }

    private void restoreSalesDraft(boolean forCredit) {
        SalesDraft d = getSalesDraft(forCredit);
        boolean initialized = forCredit ? creditDraftInitialized : cashDraftInitialized;
        if (!initialized) {
            d = new SalesDraft();
            if (forCredit) { creditDraft.products.clear(); creditDraft.boxQty.clear(); creditDraft.reamQty.clear(); creditDraftInitialized = true; }
            else { cashDraft.products.clear(); cashDraft.boxQty.clear(); cashDraft.reamQty.clear(); cashDraftInitialized = true; }
        }
        tempProducts.clear(); tempProducts.addAll(d.products);
        tempBoxes.clear(); tempBoxes.addAll(d.boxQty);
        tempReams.clear(); tempReams.addAll(d.reamQty);
        prefs.edit().putString("draftCustomer", d.customer).putString("draftAddress", d.address)
            .putString(forCredit ? "creditDraftAddress" : "cashDraftAddress", d.address)
            .putString("draftSaleBox", d.boxInput).putString("draftSaleReam", d.reamInput)
            .putString("draftSaleCash", d.cashInput).putString("draftReceivable", d.collectionInput)
            .putString("draftPartialPayment", d.partialInput).putInt("draftProduct", d.productPosition).apply();
    }

    private void switchSalesPaymentMode(boolean toCredit) {
        if (creditMode == toCredit) return;
        captureCurrentSalesDraft();
        creditMode = toCredit;
        restoreSalesDraft(toCredit);
        updatePaymentModeButtons();
        showTab(1);
    }

    private void updatePaymentModeButtons() {
        if (cashModeButton != null) {
            cashModeButton.setBackground(rounded(creditMode ? CARD_BG : NAVY_DARK, 12, creditMode ? BORDER : NAVY));
            cashModeButton.setTextColor(creditMode ? TEXT_MUTED : Color.WHITE);
            cashModeButton.setAlpha(creditMode ? 0.78f : 1.0f);
        }
        if (creditModeButton != null) {
            creditModeButton.setBackground(rounded(creditMode ? NAVY_DARK : CARD_BG, 12, creditMode ? NAVY : BORDER));
            creditModeButton.setTextColor(creditMode ? Color.WHITE : TEXT_MUTED);
            creditModeButton.setAlpha(creditMode ? 1.0f : 0.78f);
        }
    }

    private void showLanguageMenu(View anchor) {
        android.view.ContextThemeWrapper menuContext = new android.view.ContextThemeWrapper(
            this, lightTheme ? android.R.style.Theme_Material_Light_NoActionBar : android.R.style.Theme_Material_NoActionBar);
        android.widget.PopupMenu menu = new android.widget.PopupMenu(menuContext, anchor);
        menu.getMenu().add(0, 1, 0, "English" + (languageTagalog ? "" : "  ✓"));
        menu.getMenu().add(0, 2, 1, "Tagalog" + (languageTagalog ? "  ✓" : ""));
        menu.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener(){
            public boolean onMenuItemClick(android.view.MenuItem item){
                boolean useTagalog = item.getItemId() == 2;
                if (languageTagalog == useTagalog) return true;
                if (host != null && currentTab == 1) captureCurrentSalesDraft();
                if (prefs != null) { save(); prefs.edit().putBoolean("languageTagalog", useTagalog).apply(); }
                languageTagalog = useTagalog;
                makeShell();
                showTab(currentTab);
                return true;
            }
        });
        menu.show();
    }

    private void updateHeaderDate() {
        if (dateView == null) return;
        Calendar today = Calendar.getInstance();
        boolean isToday = headerDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && headerDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        dateView.setText((isToday ? "● " : "") +
                new SimpleDateFormat("EEE, dd MMM yyyy", languageTagalog ? new Locale("fil", "PH") : Locale.US).format(headerDate.getTime()));
        dateView.setTextColor(isToday ? NAVY : TEXT_MAIN);
        dateView.setContentDescription(tx("Displayed date: ") +
                new SimpleDateFormat("EEEE, dd MMMM yyyy", languageTagalog ? new Locale("fil", "PH") : Locale.US).format(headerDate.getTime()) +
                ". Swipe left or right to change date. Tap to view calendar.");
    }

    // Read-only calendar: month navigation is allowed, but individual dates cannot be selected.
    // Today's date is highlighted using the app's navy theme color.
    private void showReadOnlyCalendar() {
        final Calendar shownMonth = Calendar.getInstance();
        shownMonth.setTime(headerDate.getTime());
        shownMonth.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout panel = col();
        panel.setPadding(dp(10), dp(3), dp(10), dp(6));
        panel.setBackgroundColor(CARD_BG);

        LinearLayout monthHeader = row();
        monthHeader.setGravity(Gravity.CENTER_VERTICAL);
        monthHeader.setPadding(dp(4), 0, dp(4), 0);
        monthHeader.setBackground(rounded(NAVY_DARK, 10, NAVY));
        Button previousMonth = btn("‹");
        setSp(previousMonth, 28);
        previousMonth.setTextColor(Color.WHITE);
        previousMonth.setBackgroundColor(Color.TRANSPARENT);
        previousMonth.setMinWidth(dp(44));
        Button nextMonth = btn("›");
        setSp(nextMonth, 28);
        nextMonth.setTextColor(Color.WHITE);
        nextMonth.setBackgroundColor(Color.TRANSPARENT);
        nextMonth.setMinWidth(dp(44));
        final TextView monthTitle = label("", 17, true);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setTextColor(Color.WHITE);
        monthHeader.addView(previousMonth, lp(40, 40));
        monthHeader.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(40), 1));
        monthHeader.addView(nextMonth, lp(40, 40));
        panel.addView(monthHeader, lp(-1, -2));

        LinearLayout weekdays = row();
        String[] weekdayNames = {"S", "M", "T", "W", "T", "F", "S"};
        for (int i = 0; i < weekdayNames.length; i++) {
            TextView weekday = label(weekdayNames[i], 12, true);
            weekday.setGravity(Gravity.CENTER);
            weekday.setTextColor(TEXT_MUTED);
            weekdays.addView(weekday, new LinearLayout.LayoutParams(0, dp(27), 1));
        }
        panel.addView(weekdays, lp(-1, -2));

        final LinearLayout calendarGrid = col();
        panel.addView(calendarGrid, lp(-1, -2));

        final Runnable[] redrawCalendar = new Runnable[1];
        redrawCalendar[0] = new Runnable() {
            public void run() {
                monthTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(shownMonth.getTime()));
                calendarGrid.removeAllViews();
                Calendar firstDay = (Calendar) shownMonth.clone();
                firstDay.set(Calendar.DAY_OF_MONTH, 1);
                int offset = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
                int daysInMonth = shownMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
                Calendar today = Calendar.getInstance();
                int dayNumber = 1;
                for (int week = 0; week < 6; week++) {
                    LinearLayout weekRow = row();
                    for (int dow = 0; dow < 7; dow++) {
                        int cellIndex = week * 7 + dow;
                        if (cellIndex < offset || dayNumber > daysInMonth) {
                            TextView blank = label("", 14, false);
                            weekRow.addView(blank, new LinearLayout.LayoutParams(0, dp(34), 1));
                        } else {
                            final int day = dayNumber++;
                            TextView dayCell = label(String.valueOf(day), 14, false);
                            dayCell.setGravity(Gravity.CENTER);
                            dayCell.setTextColor(TEXT_MAIN);
                            boolean isToday = shownMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                                    && shownMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                                    && day == today.get(Calendar.DAY_OF_MONTH);
                            if (isToday) {
                                dayCell.setTextColor(Color.WHITE);
                                dayCell.setBackground(rounded(NAVY, 22, NAVY));
                            }
                            // Non-clickable cells keep this calendar view-only.
                            dayCell.setClickable(false);
                            weekRow.addView(dayCell, new LinearLayout.LayoutParams(0, dp(34), 1));
                        }
                    }
                    calendarGrid.addView(weekRow, lp(-1, -2));
                    if (dayNumber > daysInMonth) break;
                }
            }
        };
        previousMonth.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { shownMonth.add(Calendar.MONTH, -1); redrawCalendar[0].run(); }
        });
        nextMonth.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { shownMonth.add(Calendar.MONTH, 1); redrawCalendar[0].run(); }
        });
        panel.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    startX = event.getX(); startY = event.getY(); return true;
                }
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dx = event.getX() - startX, dy = event.getY() - startY;
                    if (Math.abs(dx) > dp(45) && Math.abs(dx) > Math.abs(dy)) {
                        shownMonth.add(Calendar.MONTH, dx < 0 ? 1 : -1);
                        redrawCalendar[0].run();
                    }
                    return true;
                }
                return true;
            }
        });
        redrawCalendar[0].run();
        final AlertDialog calendarDialog = new ThemedDialogBuilder().setView(panel)
                .setPositiveButton(tx("Close"), null).create();
        calendarDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                if (calendarDialog.getWindow() != null) {
                    int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(48), dp(324));
                    calendarDialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));
                    calendarDialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
                    calendarDialog.getWindow().setGravity(Gravity.CENTER);
                }
                Button close = calendarDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (close != null) close.setTextColor(NAVY);
            }
        });
        calendarDialog.show();
    }

    private void showAppMenu(final View anchor) {
        final PopupWindow popup = new PopupWindow(this);
        LinearLayout panel = col();
        panel.setPadding(dp(12), dp(8), dp(12), dp(10));
        panel.setBackgroundColor(CARD_BG);

        TextView heading = label("MENU",20,true);
        heading.setTextColor(TEXT_MAIN);
        heading.setPadding(dp(8),dp(8),dp(8),dp(12));
        panel.addView(heading,lp(-1,-2));

        String[] options={"Open","Save","Restore","Export to Excel (CSV)","Export to PDF","Set Prices","About","Calculator", lightTheme ? "Switch to Dark Theme" : "Switch to Light Theme"};
        String[] menuIcons={"📂","💾","♻️","📊","📄","₱","ℹ","🧮", lightTheme ? "☾" : "☀"};
        for(int i=0;i<options.length;i++) {
            // Subtle dividers group related actions for a cleaner, easier-to-scan menu.
            // File actions | Export actions | Settings/info | Calculator/theme.
            if(i==3 || i==5 || i==7) {
                View divider=new View(this);
                divider.setBackgroundColor(BORDER);
                LinearLayout.LayoutParams dividerLp=lp(-1,1);
                dividerLp.setMargins(dp(6),dp(5),dp(6),dp(5));
                panel.addView(divider,dividerLp);
            }
            final int choice=i;
            LinearLayout item=row();
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10),dp(4),dp(10),dp(4));
            item.setBackgroundColor(Color.TRANSPARENT);

            TextView icon=label(menuIcons[i],20,false);
            icon.setGravity(Gravity.CENTER);
            icon.setTextColor(TEXT_MAIN);
            item.addView(icon,lp(38,-1));

            TextView itemText=label(options[i],16,false);
            itemText.setTextColor(TEXT_MAIN);
            itemText.setGravity(Gravity.CENTER_VERTICAL);
            itemText.setPadding(dp(8),0,dp(4),0);
            item.addView(itemText,new LinearLayout.LayoutParams(0,-1,1));

            addPressAnimation(item);
            LinearLayout.LayoutParams itemLp=lp(-1,48);
            itemLp.setMargins(0,dp(1),0,dp(1));
            panel.addView(item,itemLp);

            item.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    popup.dismiss();
                    if(choice==0) openBackupPicker();
                    else if(choice==1) saveBackupPicker();
                    else if(choice==2) restoreLatestSavedBackup();
                    else if(choice==3) chooseExportScope(true);
                    else if(choice==4) chooseExportScope(false);
                    else if(choice==5) showPriceSettings();
                    else if(choice==6) showAbout();
                    else if(choice==7) showCalculator();
                    else {
                        lightTheme=!lightTheme;
                        prefs.edit().putBoolean("lightTheme",lightTheme).apply();
                        applyThemePalette();
                        makeShell();
                        showTab(currentTab);
                    }
                }
            });
        }

        int popupWidth=Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(320));
        popup.setContentView(panel);
        popup.setWidth(popupWidth);
        popup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(CARD_BG));
        if(android.os.Build.VERSION.SDK_INT>=21) popup.setElevation(dp(8));
        popup.showAsDropDown(anchor,0,0,Gravity.START);
    }

    private void showPriceSettings() {
        final LinearLayout form=col();
        form.setPadding(dp(10),dp(8),dp(10),dp(8));
        form.setBackgroundColor(CARD_BG);

        TextView note=label("Set selling price per REAM for each product.\nBox price is automatically 50 × the ream price.",14,false);
        note.setTextColor(TEXT_MUTED);
        form.addView(note,lp(-1,-2));

        final ArrayList<EditText> fields=new ArrayList<EditText>();
        for(int i=0;i<names.size();i++){
            LinearLayout row=row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView product=label(names.get(i),14,true);
            product.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(product,new LinearLayout.LayoutParams(0,dp(52),1));

            EditText price=edit("0",true);
            setSp(price, 16);
            price.setSelectAllOnFocus(true);
            price.setText(String.format(Locale.getDefault(),"%.2f",i<productPrices.size()?productPrices.get(i):1420.0));
            row.addView(price,lp(120,52));
            fields.add(price);

            LinearLayout.LayoutParams rowLp=lp(-1,58);
            rowLp.setMargins(0,dp(3),0,dp(3));
            form.addView(row,rowLp);
        }

        if(names.size()==0) form.addView(label("Add products first, then set their prices.",15,false));

        ScrollView scroll=new ScrollView(this);
        scroll.addView(form,new ScrollView.LayoutParams(-1,-2));

        final AlertDialog dialog=new ThemedDialogBuilder()
            .setTitle(tx("SET PRODUCT PRICES"))
            .setView(scroll)
            .setNegativeButton(tx("Cancel"),null)
            .setPositiveButton(tx("Save"),null)
            .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener(){
            public void onShow(DialogInterface d){
                applyDialogTheme(dialog);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(TEXT_MAIN);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_MAIN);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v){
                        try {
                            while(productPrices.size()<names.size()) productPrices.add(1420.0);
                            for(int i=0;i<names.size();i++){
                                double p=qty(fields.get(i).getText().toString());
                                if(p<=0) { fields.get(i).setError("Enter a price greater than 0"); return; }
                                productPrices.set(i,p);
                            }
                            save();
                            dialog.dismiss();
                            Toast.makeText(MainActivity.this,tx("Product prices saved"),Toast.LENGTH_SHORT).show();
                            showTab(currentTab);
                        } catch(Exception ex) {
                            Toast.makeText(MainActivity.this,tx("Enter valid prices"),Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    private void showAbout() {
        final AlertDialog dialog = new ThemedDialogBuilder().create();

        LinearLayout panel = col();
        panel.setPadding(dp(18), dp(18), dp(18), dp(16));
        panel.setBackground(rounded(CARD_BG, 20, BORDER));

        // Decorative header gives the About screen a more polished, layered look.
        LinearLayout hero = col();
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(12), dp(18), dp(12), dp(18));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY_DARK, NAVY});
        heroBg.setCornerRadius(dp(16));
        hero.setBackground(heroBg);

        ImageView appMark = new ImageView(this);
        appMark.setImageResource(R.drawable.atc_sales_about_icon);
        appMark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        appMark.setAdjustViewBounds(true);
        appMark.setContentDescription(tx("ATC 2x logo"));
        LinearLayout.LayoutParams markLp = lp(124, 124);
        markLp.bottomMargin = dp(8);
        hero.addView(appMark, markLp);

        TextView appName = new TextView(this);
        appName.setText(tx("SALES POINT"));
        appName.setTextColor(Color.WHITE);
        appName.setGravity(Gravity.CENTER);
        appName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setSp(appName, 20);
        hero.addView(appName, lp(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText(tx("Inventory & Sales Management"));
        subtitle.setTextColor(0xffd8fffa);
        subtitle.setGravity(Gravity.CENTER);
        setSp(subtitle, 14);
        LinearLayout.LayoutParams subLp = lp(-1, -2);
        subLp.topMargin = dp(4);
        hero.addView(subtitle, subLp);
        panel.addView(hero, lp(-1, -2));

        TextView version = new TextView(this);
        version.setText(tx("●  VERSION ") + getInstalledVersionName());
        version.setTextColor(NAVY);
        version.setGravity(Gravity.CENTER);
        version.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setSp(version, 15);
        version.setPadding(dp(14), dp(9), dp(14), dp(9));
        version.setBackground(rounded(lightTheme ? 0xffe0f2f1 : 0xff173f3d, 22, NAVY));
        LinearLayout.LayoutParams versionLp = lp(-2, -2);
        versionLp.gravity = Gravity.CENTER_HORIZONTAL;
        versionLp.topMargin = dp(16);
        versionLp.bottomMargin = dp(8);
        panel.addView(version, versionLp);

        panel.addView(sectionDivider());
        addAboutInfoRow(panel, "DEVELOPER", "Cristjan Mercolita");
        View gap = new View(this);
        panel.addView(gap, lp(1, 8));
        addAboutInfoRow(panel, "POWERED BY", "ChatGPT");

        TextView releaseNote = new TextView(this);
        releaseNote.setText(tx("UI & workflow improvements"));
        releaseNote.setTextColor(TEXT_MUTED);
        releaseNote.setGravity(Gravity.CENTER);
        setSp(releaseNote, 14);
        releaseNote.setPadding(dp(8), dp(12), dp(8), dp(4));
        panel.addView(releaseNote, lp(-1, -2));

        Button checkUpdate = btn("CHECK UPDATE");
        styleSalesActionButton(checkUpdate);
        LinearLayout.LayoutParams checkUpdateLp = lp(-1, 44);
        checkUpdateLp.topMargin = dp(12);
        panel.addView(checkUpdate, checkUpdateLp);
        checkUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                updateDialogShown = false;
                checkForUpdates(true);
            }
        });

        Button done = btn("DONE");
        styleSalesActionButton(done);
        LinearLayout.LayoutParams doneLp = lp(-1, 44);
        doneLp.topMargin = dp(12);
        panel.addView(done, doneLp);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setView(panel);
        dialog.show();
        applyDialogTheme(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = getResources().getDisplayMetrics().widthPixels - dp(36);
            dialog.getWindow().setLayout(Math.min(width, dp(420)), -2);
        }
    }

    private void addAboutInfoRow(LinearLayout parent, String heading, String value) {
        LinearLayout item = col();
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setBackground(rounded(lightTheme ? 0xfff1f5f9 : HEADER_BG, 12, BORDER));

        TextView label = new TextView(this);
        label.setText(heading);
        label.setTextColor(NAVY);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setSp(label, 14);
        item.addView(label, lp(-1, -2));

        TextView detail = new TextView(this);
        detail.setText(value);
        detail.setTextColor(TEXT_MAIN);
        setSp(detail, 16);
        LinearLayout.LayoutParams detailLp = lp(-1, -2);
        detailLp.topMargin = dp(3);
        item.addView(detail, detailLp);

        LinearLayout.LayoutParams itemLp = lp(-1, -2);
        parent.addView(item, itemLp);
    }

    private void openBackupPicker() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i,REQUEST_OPEN_BACKUP); }
        catch(Exception e) { Toast.makeText(this,tx("Unable to open file picker"),Toast.LENGTH_LONG).show(); }
    }
    private void saveBackupPicker() {
        save();
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE,"InventorySalesBackup.backup");
        try { startActivityForResult(i,REQUEST_SAVE_BACKUP); }
        catch(Exception e) { Toast.makeText(this,tx("Unable to create backup file"),Toast.LENGTH_LONG).show(); }
    }
    private int exportScope = 0; // 0 inventory, 1 sales list
    private void chooseExportScope(final boolean csvFormat) {
        final String[] scopes={"Inventory only (Beginning, Ending & Stock In)","Sales list only (Customers & products)"};
        new ThemedDialogBuilder().setTitle(csvFormat?"Export CSV":"Export PDF").setItems(scopes,new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int which){exportScope=which;if(csvFormat)exportCsvPicker();else exportPdfPicker();}
        }).show();
    }
    private void exportCsvPicker() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE,exportScope==0?"InventoryReport.csv":"SalesList.csv");
        try { startActivityForResult(i,REQUEST_EXPORT_CSV); }
        catch(Exception e) { Toast.makeText(this,tx("Unable to create CSV file"),Toast.LENGTH_LONG).show(); }
    }
    private void exportPdfPicker() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_TITLE,exportScope==0?"InventoryReport.pdf":"SalesList.pdf");
        try { startActivityForResult(i,REQUEST_EXPORT_PDF); }
        catch(Exception e) { Toast.makeText(this,tx("Unable to create PDF file"),Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) {
            if(requestCode==REQUEST_BACKUP_FOLDER) continueStartupWithoutRestore();
            return;
        }
        Uri uri=data.getData();
        try {
            if(requestCode==REQUEST_BACKUP_FOLDER) {
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Uri backupFolder = ensureSalesPointFolder(uri);
                prefs.edit().putString("backupTreeUri",backupFolder.toString()).apply();
                Uri existing=findAutomaticBackup(backupFolder);
                if(existing!=null) checkForAutomaticBackup(backupFolder);
                else {
                    writeAutomaticBackup();
                    Toast.makeText(this,tx("Automatic backup folder is ready"),Toast.LENGTH_LONG).show();
                    continueStartupWithoutRestore();
                }
            } else if(requestCode==REQUEST_OPEN_BACKUP) {
                // Opening a backup now previews it first; no app data is changed here.
                showBackupPreview(uri);
            } else if(requestCode==REQUEST_SAVE_BACKUP) {
                OutputStream out=getContentResolver().openOutputStream(uri);
                Properties p=backupProperties(); p.store(out,"Inventory & Sales backup"); out.close();
                Toast.makeText(this,tx("Backup file saved"),Toast.LENGTH_LONG).show();
            } else if(requestCode==REQUEST_EXPORT_CSV) {
                OutputStream out=getContentResolver().openOutputStream(uri);
                out.write(buildCsvReport(exportScope).getBytes("UTF-8")); out.close();
                Toast.makeText(this,tx("Excel-compatible CSV exported"),Toast.LENGTH_LONG).show();
            } else if(requestCode==REQUEST_EXPORT_PDF) {
                OutputStream out=getContentResolver().openOutputStream(uri);
                writePdfReport(out,exportScope); out.close();
                Toast.makeText(this,tx("PDF report exported"),Toast.LENGTH_LONG).show();
            }
        } catch(Exception e) {
            Toast.makeText(this,tx("File operation failed: ")+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void showBackupPreview(final Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Unable to read the selected file");
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = in.read(chunk)) != -1) buffer.write(chunk, 0, count);
        in.close();
        byte[] bytes = buffer.toByteArray();

        Properties preview = new Properties();
        java.io.ByteArrayInputStream propInput = new java.io.ByteArrayInputStream(bytes);
        preview.load(propInput);
        propInput.close();
        if (!preview.containsKey("version")) {
            throw new IOException("This file is not a valid Sales Point backup (missing version field)");
        }
        validateBackupPreviewCount(preview, "productCount", 1000);
        validateBackupPreviewCount(preview, "saleCount", 10000);
        validateBackupPreviewCount(preview, "endingCount", 10000);
        validateBackupPreviewCount(preview, "withdrawCount", 10000);
        validateBackupPreviewCount(preview, "tempCount", 10000);

        String name = uri.getLastPathSegment();
        if (name == null || name.length() == 0) name = "Selected backup file";
        StringBuilder details = new StringBuilder();
        details.append("BACKUP: ").append(name).append("\n");
        details.append("SIZE: ").append(bytes.length).append(" bytes\n");
        details.append("FORMAT: ").append(preview.getProperty("version", "Unknown")).append("\n\n");

        details.append("BEGINNING INVENTORY (").append(preview.getProperty("beginningDate", "No date")).append(")\n");
        int productCount = propertyInt(preview, "productCount", 0);
        if (productCount == 0) details.append("No products\n");
        for (int i = 0; i < productCount; i++) {
            String product = preview.getProperty("name." + i, "Product " + (i + 1));
            details.append(product).append(" — Box: ")
                .append(preview.getProperty("beginning.boxes." + i, preview.getProperty("boxes." + i, "0")))
                .append(", Ream: ")
                .append(preview.getProperty("beginning.reams." + i, preview.getProperty("reams." + i, "0")))
                .append("\n");
        }

        details.append("\nWITHDRAWAL / STOCK IN\n");
        int withdrawCount = propertyInt(preview, "withdrawCount", 0);
        if (withdrawCount == 0) details.append("No withdrawal records\n");
        for (int i = 0; i < withdrawCount; i++) {
            details.append(formatBackupInventoryRecord(preview.getProperty("withdraw." + i, ""))).append("\n");
        }

        details.append("\nENDING INVENTORY (").append(preview.getProperty("endingDate", "No date")).append(")\n");
        int endingCount = propertyInt(preview, "endingCount", 0);
        if (endingCount == 0) details.append("No ending inventory records\n");
        for (int i = 0; i < endingCount; i++) {
            details.append(formatBackupInventoryRecord(preview.getProperty("ending." + i, ""))).append("\n");
        }

        details.append("\nSALES SUMMARY — JANUARY TO DECEMBER\n");
        details.append("Grouped by month, Week 1–4 (days 1–7, 8–14, 15–21, 22–end), Monday–Friday.\n");
        appendBackupSalesSummary(details, preview);

        ScrollView scroll = new ScrollView(this);
        TextView content = new TextView(this);
        content.setText(details.toString());
        content.setTextColor(TEXT_MAIN);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        content.setTypeface(Typeface.MONOSPACE);
        int pad = dp(14);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        scroll.setBackgroundColor(CARD_BG);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(2), dp(2), dp(2), dp(2));
        wrapper.addView(scroll, new LinearLayout.LayoutParams(-1, dp(360)));

        new AlertDialog.Builder(this)
            .setTitle(tx("Backup Preview"))
            .setMessage(tx("Review the backup contents below. Your current data will not change unless you choose Restore."))
            .setView(wrapper)
            .setPositiveButton(tx("Restore Backup"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    confirmBackupRestore(uri);
                }
            })
            .setNegativeButton(tx("Cancel"), null)
            .show();
    }

    private String formatBackupInventoryRecord(String record) {
        if (record == null || record.trim().length() == 0) return "No details";
        String[] lines = record.split("\n");
        String date = "", product = "", quantity = "";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("Date:")) date = line.substring(5).trim();
            else if (line.startsWith("Box:")) quantity = line;
            else if (line.length() > 0) product = line;
        }
        StringBuilder result = new StringBuilder();
        if (date.length() > 0) result.append(date).append(" — ");
        result.append(product.length() > 0 ? product : "Product not specified");
        if (quantity.length() > 0) result.append(" — ").append(quantity);
        return result.toString();
    }

    private void appendBackupSalesSummary(StringBuilder out, Properties p) {
        final String[] months = {"January","February","March","April","May","June","July","August","September","October","November","December"};
        final String[] weekNames = {"Week 1 (1–7)","Week 2 (8–14)","Week 3 (15–21)","Week 4 (22–end)"};
        final String[] weekdays = {"Monday","Tuesday","Wednesday","Thursday","Friday"};
        SimpleDateFormat parser = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
        parser.setLenient(false);
        int count = propertyInt(p, "saleCount", 0);
        boolean any = false;
        double yearTotal = 0;
        for (int month = 0; month < 12; month++) {
            StringBuilder monthText = new StringBuilder();
            double monthTotal = 0;
            int firstDataDay = 32;
            int lastDataDay = 0;
            for (int week = 0; week < 4; week++) {
                StringBuilder weekText = new StringBuilder();
                double weekTotal = 0;
                for (int day = 0; day < 5; day++) {
                    double dayBoxes = 0;
                    double dayReams = 0;
                    double dayTotal = 0;
                    boolean hasDaySales = false;
                    for (int i = 0; i < count; i++) {
                        try {
                            Date date = parser.parse(p.getProperty("sale.date." + i, ""));
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(date);
                            int dom = cal.get(Calendar.DAY_OF_MONTH);
                            if (cal.get(Calendar.MONTH) != month) continue;
                            // Track the actual date range represented by saved sales in this month.
                            if (dom < firstDataDay) firstDataDay = dom;
                            if (dom > lastDataDay) lastDataDay = dom;
                            if (dom < week * 7 + 1 || dom > (week == 3 ? 31 : (week + 1) * 7) ||
                                cal.get(Calendar.DAY_OF_WEEK) != day + 2) continue;
                            double boxes = propertyDouble(p, "sale.box." + i, 0);
                            double reams = propertyDouble(p, "sale.ream." + i, 0);
                            double price = propertyDouble(p, "sale.price." + i, 0);
                            double amount = boxes * (price * 50.0) + reams * price;
                            dayBoxes += boxes;
                            dayReams += reams;
                            dayTotal += amount;
                            hasDaySales = true;
                        } catch (Exception ignored) { }
                    }
                    if (hasDaySales) {
                        weekText.append(weekdays[day]).append("\n");
                        weekText.append("  Total product quantity: Box: ").append(qtyText(dayBoxes))
                            .append(", Ream: ").append(qtyText(dayReams)).append("\n");
                        weekText.append("  DAILY TOTAL AMOUNT: ").append(moneyText(dayTotal)).append("\n");
                        weekTotal += dayTotal;
                    }
                }
                if (weekText.length() > 0) {
                    weekText.insert(0, weekNames[week] + "\n");
                    weekText.append("  WEEKLY TOTAL AMOUNT: ").append(moneyText(weekTotal)).append("\n");
                    monthTotal += weekTotal;
                    monthText.append(weekText);
                }
            }
            if (monthText.length() > 0) {
                any = true;
                out.append("\n").append(months[month]);
                if (firstDataDay <= lastDataDay) {
                    out.append(" (days ").append(firstDataDay).append("–").append(lastDataDay).append(")");
                }
                out.append("\n").append(monthText);
                out.append("MONTHLY TOTAL AMOUNT: ").append(moneyText(monthTotal)).append("\n");
                yearTotal += monthTotal;
            }
        }
        if (!any) out.append("No Monday–Friday sales records found for January–December.\n");
        else out.append("\nYEAR TOTAL AMOUNT (Monday–Friday): ").append(moneyText(yearTotal)).append("\n");
    }

    private void validateBackupPreviewCount(Properties p, String key, int max) throws IOException {
        String value = p.getProperty(key, "0");
        try {
            int n = Integer.parseInt(value);
            if (n < 0 || n > max) throw new IOException("Backup contains an invalid " + key + " value");
        } catch (NumberFormatException e) {
            throw new IOException("Backup contains an invalid " + key + " value");
        }
    }

    private void confirmBackupRestore(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("Unable to read the selected backup");
            Properties p = new Properties();
            p.load(in);
            in.close();
            if (!p.containsKey("version")) throw new IOException("Invalid Sales Point backup file");
            importBackupProperties(p);
            save();
            showTab(currentTab);
            Toast.makeText(this,tx("Backup restored successfully"), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,tx("Could not restore backup: ") + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Properties backupProperties() {
        Properties p=new Properties();
        p.setProperty("version","1"); p.setProperty("advancedMode","true"); p.setProperty("lightTheme",String.valueOf(lightTheme)); p.setProperty("beginningDate",beginningDate==null||beginningDate.length()==0?today():beginningDate); p.setProperty("endingDate",endingDate==null||endingDate.length()==0?today():endingDate); p.setProperty("salesCutoffDate",salesCutoffDate==null?"":salesCutoffDate); p.setProperty("withdrawViewDate",withdrawViewDate==null||withdrawViewDate.length()==0?today():withdrawViewDate); p.setProperty("productCount",String.valueOf(names.size()));
        for(int i=0;i<names.size();i++){p.setProperty("name."+i,names.get(i));p.setProperty("boxes."+i,String.valueOf(boxes.get(i)));p.setProperty("reams."+i,String.valueOf(reams.get(i)));p.setProperty("beginning.boxes."+i,String.valueOf(i<beginningBoxes.size()?beginningBoxes.get(i):boxes.get(i)));p.setProperty("beginning.reams."+i,String.valueOf(i<beginningReams.size()?beginningReams.get(i):reams.get(i)));p.setProperty("price."+i,String.valueOf(i<productPrices.size()?productPrices.get(i):1420.0));}
        p.setProperty("saleCount",String.valueOf(sales.size()));
        for(int i=0;i<sales.size();i++){Sale x=sales.get(i);p.setProperty("sale.date."+i,x.date);p.setProperty("sale.customer."+i,x.customer);p.setProperty("sale.address."+i,x.address==null?"":x.address);p.setProperty("sale.product."+i,x.product);p.setProperty("sale.box."+i,String.valueOf(x.box));p.setProperty("sale.ream."+i,String.valueOf(x.ream));p.setProperty("sale.cash."+i,String.valueOf(x.cash));p.setProperty("sale.receivable."+i,String.valueOf(x.receivable));p.setProperty("sale.price."+i,String.valueOf(x.unitPrice));p.setProperty("sale.partial."+i,String.valueOf(x.partial));p.setProperty("sale.balance."+i,String.valueOf(x.balance));p.setProperty("sale.credit."+i,String.valueOf(x.credit));}
        p.setProperty("beginningHistoryCount",String.valueOf(beginningHistory.size()));for(int i=0;i<beginningHistory.size();i++)p.setProperty("beginningHistory."+i,beginningHistory.get(i));
        p.setProperty("endingCount",String.valueOf(endingRecords.size()));for(int i=0;i<endingRecords.size();i++)p.setProperty("ending."+i,endingRecords.get(i));
        p.setProperty("withdrawCount",String.valueOf(withdrawRecords.size()));for(int i=0;i<withdrawRecords.size();i++)p.setProperty("withdraw."+i,withdrawRecords.get(i));
        p.setProperty("tempCount",String.valueOf(tempProducts.size()));for(int i=0;i<tempProducts.size();i++){p.setProperty("temp.product."+i,tempProducts.get(i));p.setProperty("temp.box."+i,String.valueOf(tempBoxes.get(i)));p.setProperty("temp.ream."+i,String.valueOf(tempReams.get(i)));}
        p.setProperty("currentTab",String.valueOf(currentTab));
        p.setProperty("draftCustomer",customer==null?prefs.getString("draftCustomer",""):customer.getText().toString());
        p.setProperty("draftAddress",customerAddress==null?prefs.getString("draftAddress",""):customerAddress.getText().toString());
        p.setProperty("draftSaleBox",saleBox==null?prefs.getString("draftSaleBox",""):saleBox.getText().toString());
        p.setProperty("draftSaleReam",saleReam==null?prefs.getString("draftSaleReam",""):saleReam.getText().toString());
        p.setProperty("draftSaleCash",saleCash==null?prefs.getString("draftSaleCash","0"):saleCash.getText().toString());
        p.setProperty("draftReceivable",receivableCollection==null?prefs.getString("draftReceivable","0"):receivableCollection.getText().toString());
        p.setProperty("draftProduct",String.valueOf(productSpinner==null?prefs.getInt("draftProduct",0):productSpinner.getSelectedItemPosition()));
        return p;
    }
    private int propertyInt(Properties p,String key,int fallback){try{return Integer.parseInt(p.getProperty(key,String.valueOf(fallback)));}catch(Exception e){return fallback;}}
    private double propertyDouble(Properties p,String key,double fallback){try{return Double.parseDouble(p.getProperty(key,String.valueOf(fallback)));}catch(Exception e){return fallback;}}
    private void importBackupProperties(Properties p) {
        beginningDate=p.getProperty("beginningDate",today());
        endingDate=p.getProperty("endingDate",today());
        salesCutoffDate=p.getProperty("salesCutoffDate","");
        // Ignore legacy mode flags in backups; this app now uses one unified mode.
        lightTheme=Boolean.parseBoolean(p.getProperty("lightTheme",String.valueOf(lightTheme)));
        applyThemePalette();
        withdrawViewDate=p.getProperty("withdrawViewDate",today());
        customer=null; customerAddress=null; saleBox=null; saleReam=null; saleCash=null; receivableCollection=null; productSpinner=null;
        names.clear();boxes.clear();reams.clear();productPrices.clear();beginningBoxes.clear();beginningReams.clear();sales.clear();endingRecords.clear();beginningHistory.clear();withdrawRecords.clear();tempProducts.clear();tempBoxes.clear();tempReams.clear();
        int n=Math.min(1000,Math.max(0,propertyInt(p,"productCount",0)));
        for(int i=0;i<n;i++){names.add(p.getProperty("name."+i,"Product "+(i+1)));boxes.add(propertyDouble(p,"boxes."+i,0));reams.add(propertyDouble(p,"reams."+i,0));beginningBoxes.add(propertyDouble(p,"beginning.boxes."+i,propertyDouble(p,"boxes."+i,0)));beginningReams.add(propertyDouble(p,"beginning.reams."+i,propertyDouble(p,"reams."+i,0)));productPrices.add(propertyDouble(p,"price."+i,1420.0));}
        n=Math.min(10000,Math.max(0,propertyInt(p,"saleCount",0)));
        for(int i=0;i<n;i++){String spName=p.getProperty("sale.product."+i,"");double fallbackPrice=reamPrice(spName);sales.add(new Sale(p.getProperty("sale.date."+i,""),p.getProperty("sale.customer."+i,""),spName,propertyDouble(p,"sale.box."+i,0),propertyDouble(p,"sale.ream."+i,0),propertyDouble(p,"sale.cash."+i,0),propertyDouble(p,"sale.receivable."+i,0),propertyDouble(p,"sale.price."+i,fallbackPrice)));sales.get(sales.size()-1).address=p.getProperty("sale.address."+i,"");sales.get(sales.size()-1).partial=propertyDouble(p,"sale.partial."+i,0);sales.get(sales.size()-1).balance=propertyDouble(p,"sale.balance."+i,Math.max(0,sales.get(sales.size()-1).amount()-sales.get(sales.size()-1).cash));sales.get(sales.size()-1).credit=Boolean.parseBoolean(p.getProperty("sale.credit."+i,"false"));}
        n=Math.min(10000,Math.max(0,propertyInt(p,"beginningHistoryCount",0)));for(int i=0;i<n;i++)beginningHistory.add(p.getProperty("beginningHistory."+i,""));
        n=Math.min(10000,Math.max(0,propertyInt(p,"endingCount",0)));for(int i=0;i<n;i++)endingRecords.add(p.getProperty("ending."+i,""));
        n=Math.min(10000,Math.max(0,propertyInt(p,"withdrawCount",0)));for(int i=0;i<n;i++)withdrawRecords.add(p.getProperty("withdraw."+i,""));
        n=Math.min(10000,Math.max(0,propertyInt(p,"tempCount",0)));for(int i=0;i<n;i++){tempProducts.add(p.getProperty("temp.product."+i,""));tempBoxes.add(propertyDouble(p,"temp.box."+i,0));tempReams.add(propertyDouble(p,"temp.ream."+i,0));}
        currentTab=propertyInt(p,"currentTab",1);
        if(currentTab<0 || currentTab>7 || currentTab==3 || currentTab==4) currentTab=1;
        prefs.edit().putString("draftCustomer",p.getProperty("draftCustomer","")).putString("draftAddress",p.getProperty("draftAddress","")).putString("draftSaleBox",p.getProperty("draftSaleBox","")).putString("draftSaleReam",p.getProperty("draftSaleReam","")).putString("draftSaleCash",p.getProperty("draftSaleCash","0")).putString("draftReceivable",p.getProperty("draftReceivable","0")).putString("draftPartialPayment",p.getProperty("draftPartialPayment","0")).putInt("draftProduct",propertyInt(p,"draftProduct",0)).apply();
    }
    private String csv(String s){if(s==null)s="";return "\""+s.replace("\"","\"\"")+"\"";}
    private String buildCsvReport(int scope) {
        StringBuilder b=new StringBuilder();
        if(scope==1) {
            b.append("SALES LIST\r\nGenerated,").append(csv(today())).append("\r\n");
            b.append("Date,Customer,Product,Boxes,Reams");
            b.append(",Total Amount");
            b.append("\r\n");
            for(Sale x:sales){b.append(csv(x.date)).append(',').append(csv(x.customer)).append(',').append(csv(x.product)).append(',').append(qtyText(x.box)).append(',').append(qtyText(x.ream));b.append(',').append(String.format(Locale.US,"%.2f",x.amount()));b.append("\r\n");}
            {Calendar now=Calendar.getInstance();int week=now.get(Calendar.WEEK_OF_YEAR),month=now.get(Calendar.MONTH),year=now.get(Calendar.YEAR);double weeklyCash=0,weeklyRec=0,monthlyCash=0,monthlyRec=0;for(Sale x:sales){try{Date dt=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).parse(x.date);Calendar c=Calendar.getInstance();c.setTime(dt);if(c.get(Calendar.YEAR)==year&&c.get(Calendar.WEEK_OF_YEAR)==week){weeklyCash+=x.cash;weeklyRec+=x.receivable;}if(c.get(Calendar.YEAR)==year&&c.get(Calendar.MONTH)==month){monthlyCash+=x.cash;monthlyRec+=x.receivable;}}catch(Exception ignored){}}b.append("\r\nPAYMENT SUMMARY\r\nPeriod,Cash,Receivables,Cheque\r\n");b.append("This week,").append(weeklyCash).append(',').append(weeklyRec).append(",Not recorded in current app data\r\n");b.append("This month,").append(monthlyCash).append(',').append(monthlyRec).append(",Not recorded in current app data\r\n");}
            return b.toString();
        }
        b.append("INVENTORY REPORT\r\nGenerated,").append(csv(today())).append("\r\n");
        b.append("\r\nBEGINNING INVENTORY\r\nDate,").append(csv(beginningDate)).append("\r\nProduct,Boxes,Reams");b.append(",Amount");b.append("\r\n");double beginB=0,beginR=0,beginAmount=0;
        for(int i=0;i<names.size();i++){double bx=i<beginningBoxes.size()?beginningBoxes.get(i):boxes.get(i),rx=i<beginningReams.size()?beginningReams.get(i):reams.get(i),amt=(bx*50+rx)*reamPrice(names.get(i));beginB+=bx;beginR+=rx;beginAmount+=amt;b.append(csv(names.get(i))).append(',').append(qtyText(bx)).append(',').append(qtyText(rx));b.append(',').append(String.format(Locale.US,"%.2f",amt));b.append("\r\n");}b.append("TOTAL,").append(qtyText(beginB)).append(',').append(qtyText(beginR));b.append(',').append(String.format(Locale.US,"%.2f",beginAmount));b.append("\r\n");
        b.append("\r\nENDING INVENTORY\r\nDate,").append(csv(endingDate)).append("\r\nRecord\r\n");for(String rec:endingRecords)b.append(csv(rec.replace("\n"," | "))).append("\r\n");
        b.append("\r\nSTOCK IN\r\nDate,").append(csv(withdrawViewDate)).append("\r\nRecord\r\n");for(String rec:withdrawRecords)b.append(csv(rec.replace("\n"," | "))).append("\r\n");
        return b.toString();
    }
    private void writePdfReport(OutputStream out,int scope) throws IOException {
        PdfDocument doc=new PdfDocument();Paint paint=new Paint();int pageNo=1,y=42;PdfDocument.Page page=null;Canvas canvas=null;ArrayList<String> lines=new ArrayList<String>();
        if(scope==1){lines.add("SALES LIST");lines.add("Generated: "+today());lines.add("");for(Sale x:sales){String line=x.date+" | Customer: "+x.customer+" | Address: "+(x.address==null?"":x.address)+" | Product: "+x.product+" | Box: "+qtyText(x.box)+" | Ream: "+qtyText(x.ream);line+=" | Amount: "+moneyText(x.amount());lines.add(line);}{double wc=0,wr=0,mc=0,mr=0;Calendar now=Calendar.getInstance();for(Sale x:sales){try{Date dt=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).parse(x.date);Calendar c=Calendar.getInstance();c.setTime(dt);if(c.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&c.get(Calendar.WEEK_OF_YEAR)==now.get(Calendar.WEEK_OF_YEAR)){wc+=x.cash;wr+=x.receivable;}if(c.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&c.get(Calendar.MONTH)==now.get(Calendar.MONTH)){mc+=x.cash;mr+=x.receivable;}}catch(Exception ignored){}}lines.add("");lines.add("PAYMENT SUMMARY");lines.add("This week - Cash: "+moneyText(wc)+" | Receivables: "+moneyText(wr)+" | Cheque: Not recorded");lines.add("This month - Cash: "+moneyText(mc)+" | Receivables: "+moneyText(mr)+" | Cheque: Not recorded");}}
        else {lines.add("INVENTORY REPORT");lines.add("Generated: "+today());lines.add("");lines.add("BEGINNING INVENTORY (Date: "+beginningDate+")");double bq=0,rq=0,am=0;for(int i=0;i<names.size();i++){double bx=i<beginningBoxes.size()?beginningBoxes.get(i):boxes.get(i),rx=i<beginningReams.size()?beginningReams.get(i):reams.get(i),a=(bx*50+rx)*reamPrice(names.get(i));bq+=bx;rq+=rx;am+=a;lines.add(names.get(i)+" | Box: "+qtyText(bx)+" | Ream: "+qtyText(rx)+" | Amount: "+moneyText(a));}lines.add("TOTAL | Box: "+qtyText(bq)+" | Ream: "+qtyText(rq)+" | Amount: "+moneyText(am));lines.add("");lines.add("ENDING INVENTORY (Date: "+endingDate+")");for(String rec:endingRecords)lines.add(rec.replace("\n"," | "));lines.add("");lines.add("STOCK IN (Date: "+withdrawViewDate+")");for(String rec:withdrawRecords)lines.add(rec.replace("\n"," | "));}
        try{for(String original:lines){String line=original;while(page==null||y>790){if(page!=null)doc.finishPage(page);PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pageNo++).create();page=doc.startPage(info);canvas=page.getCanvas();y=42;break;}paint.setColor(Color.BLACK);paint.setTextSize((line.equals("INVENTORY REPORT")||line.equals("SALES LIST")||line.contains("INVENTORY")||line.equals("STOCK IN")||line.contains("SUMMARY"))?14:10);paint.setTypeface(Typeface.DEFAULT);while(line.length()>90){canvas.drawText(line.substring(0,90),30,y,paint);y+=16;line=line.substring(90);if(y>790){doc.finishPage(page);PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pageNo++).create();page=doc.startPage(info);canvas=page.getCanvas();y=42;}}canvas.drawText(line,30,y,paint);y+=18;}if(page!=null)doc.finishPage(page);doc.writeTo(out);}finally{doc.close();}
    }

    private boolean sameInventoryDate(String first, String second) {
        if (first == null || second == null) return false;
        String[] patterns = {"dd-MMM-yyyy", "d-MMM-yyyy", "dd-MM-yyyy", "yyyy-MM-dd"};
        Locale[] locales = {Locale.US, Locale.getDefault(), Locale.UK};
        for (String pattern : patterns) {
            for (Locale locale : locales) {
                try {
                    SimpleDateFormat fmt = new SimpleDateFormat(pattern, locale);
                    fmt.setLenient(false);
                    Date a = fmt.parse(first.trim());
                    Date b = fmt.parse(second.trim());
                    if (a != null && b != null) {
                        Calendar ca = Calendar.getInstance(); ca.setTime(a);
                        Calendar cb = Calendar.getInstance(); cb.setTime(b);
                        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
                    }
                } catch (Exception ignored) { }
            }
        }
        return first.trim().equalsIgnoreCase(second.trim());
    }

    private String today(){return new SimpleDateFormat("dd-MMM-yyyy",Locale.getDefault()).format(new Date());}
    private void showTab(int n) {
        // Inventory subpages (Beginning, Ending, Withdraw) use internal page IDs 5-7.
        // Do not clamp these valid destinations back to the Sales page.
        int maxTab = 8;
        if(n<0 || n>maxTab) n = 1;
        if(prefs!=null) save();
        // Opening Ending Inventory from another page starts on today's date.
        // Refreshing the same page (for example after using its date picker) keeps the selected date.
        if(n==6 && n!=currentTab) endingDate=today();
        // Keep a back stack of pages actually navigated between. Refreshes on
        // the current page do not add history.
        if(n!=currentTab && !navigatingBack) {
            tabHistory.add(currentTab);
            if(tabHistory.size()>40) tabHistory.remove(0);
        }
        // Tab taps also slide in the direction of navigation. Swipes can set
        // animationDirection beforehand, so preserve that direction when set.
        if(animationDirection==0 && n!=currentTab) {
            animationDirection = n>currentTab ? 1 : -1;
        }
        currentTab=n;
        if(tabButtons!=null) {
            int selectedTab = (n==0 || n>=5) ? 0 : (n==1 ? 1 : 2);
            for(int i=0;i<tabButtons.length;i++) {
                boolean selected = (i==selectedTab);
                tabButtons[i].setBackground(rounded(selected ? NAVY_DARK : HEADER_BG, 12, selected ? NAVY : BORDER));
                tabButtons[i].setTextColor(selected ? Color.WHITE : TEXT_MAIN);
                tabButtons[i].setElevation(selected && android.os.Build.VERSION.SDK_INT>=21 ? dp(2) : 0);
            }
        }
        if(prefs!=null) prefs.edit().putInt("lastTab",n).apply();
        if(paymentModeNav!=null) paymentModeNav.setVisibility(n==1 ? View.VISIBLE : View.GONE);
        updateHeaderDate();
        host.removeAllViews();
        if(n==0) buildInventory();
        else if(n==1) buildSalesInput();
        else if(n==2) buildSalesList();
        else if(n==3) buildSalesPeriodPage(true);
        else if(n==4) buildSalesPeriodPage(false);
        else if(n==5) buildStockPage("BEGINNING INVENTORY");
        else if(n==6) buildStockPage("ENDING INVENTORY");
        else if(n==7) buildStockPage("STOCK IN");
        else if(n==8) buildCollectionPage();
        if(animationDirection!=0 && host.getChildCount()>0) {
            float width=getResources().getDisplayMetrics().widthPixels;
            TranslateAnimation slide=new TranslateAnimation(animationDirection>0?width*0.12f:-width*0.12f,0,0,0);
            slide.setDuration(230); slide.setInterpolator(new android.view.animation.DecelerateInterpolator());
            AlphaAnimation fade=new AlphaAnimation(0.78f,1f); fade.setDuration(230);
            AnimationSet transition=new AnimationSet(true); transition.addAnimation(slide); transition.addAnimation(fade);
            host.getChildAt(0).startAnimation(transition);
            animationDirection=0;
        }
    }
    private int find(String s){for(int i=0;i<names.size();i++)if(names.get(i).equalsIgnoreCase(s.trim()))return i;return -1;}
    private double qty(String s){
        if(s.trim().length()==0)return 0.0;
        double v=Double.parseDouble(s.trim());
        if(Double.isNaN(v)||Double.isInfinite(v)||v<0)throw new NumberFormatException();
        return v;
    }
    private String qtyText(double value) {
        double rounded=Math.round(value*10.0)/10.0;
        if(Math.abs(rounded-Math.round(rounded))<0.0000001) return String.valueOf((long)Math.round(rounded));
        return String.format(Locale.getDefault(),"%.1f",rounded);
    }
    private double saleAmount(String product, double box, double ream) {
        int idx=find(product);
        double reamPrice=reamPrice(product);
        return box*(reamPrice*50.0) + ream*reamPrice;
    }
    private double saleAmount(double box, double ream) {
        return box*71000.0 + ream*1420.0;
    }
    private double reamPrice(String product) { int idx=find(product); return (idx>=0 && idx<productPrices.size())?productPrices.get(idx):1420.0; }
    private String moneyText(double value) {
        return String.format(Locale.getDefault(),"₱%,.2f",value);
    }

    private void showCalculator() {
        final LinearLayout panel=col();
        panel.setPadding(dp(16),dp(10),dp(16),dp(12));
        panel.setBackgroundColor(CARD_BG);

        // Calculator display: keep the complete expression visible while typing.
        final TextView display=label("0",30,true);
        display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        display.setTextColor(TEXT_MAIN);
        display.setSingleLine(true);
        display.setEllipsize(android.text.TextUtils.TruncateAt.START);
        display.setPadding(dp(12),dp(4),dp(12),dp(4));
        display.setBackground(rounded(FIELD_BG,12,BORDER));
        panel.addView(display,lp(-1,62));

        final String[] expression={""};
        final boolean[] justEvaluated={false};

        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount(5);
        grid.setUseDefaultMargins(false);
        panel.addView(grid,lp(-1,-2));

        String[] keys={
            "7","8","9","÷",
            "4","5","6","×",
            "1","2","3","−",
            "0",".","C","+",
            "="
        };

        for(final String key:keys){
            Button b=btn(key);
            setSp(b, 20);
            b.setTextColor(TEXT_MAIN);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setBackground(rounded(key.equals("=")?NAVY_DARK:CARD_BG,10,BORDER));

            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
            gp.height=dp(58);
            gp.setMargins(dp(4),dp(4),dp(4),dp(4));
            if(key.equals("=")){
                // Make the equals key span the full calculator width, as requested.
                gp.width=0;
                gp.columnSpec=GridLayout.spec(0,4,1f);
            } else {
                gp.width=0;
                gp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f);
            }
            grid.addView(b,gp);

            b.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    String current=expression[0];

                    if(key.matches("[0-9]")){
                        if(justEvaluated[0]){
                            current="";
                            justEvaluated[0]=false;
                        }
                        expression[0]=current+key;
                        display.setText(expression[0]);

                    } else if(key.equals(".")){
                        if(justEvaluated[0]){
                            current="";
                            justEvaluated[0]=false;
                        }
                        // Do not allow two decimals in the current number.
                        String[] parts=current.split("[+−×÷]");
                        String last=parts.length==0?"":parts[parts.length-1];
                        if(!last.contains(".")){
                            if(last.length()==0) expression[0]=current+"0.";
                            else expression[0]=current+".";
                            display.setText(expression[0]);
                        }

                    } else if(key.equals("C")){
                        expression[0]="";
                        justEvaluated[0]=false;
                        display.setText(tx("0"));

                    } else if(key.equals("+")||key.equals("−")||key.equals("×")||key.equals("÷")){
                        if(justEvaluated[0]) justEvaluated[0]=false;
                        if(current.length()==0) return;
                        char last=current.charAt(current.length()-1);
                        if(last=='+'||last=='−'||last=='×'||last=='÷'){
                            expression[0]=current.substring(0,current.length()-1)+key;
                        } else {
                            expression[0]=current+key;
                        }
                        display.setText(expression[0]);

                    } else if(key.equals("=")){
                        if(current.length()==0) return;
                        char last=current.charAt(current.length()-1);
                        if(last=='+'||last=='−'||last=='×'||last=='÷') return;
                        try {
                            double result=evaluateExpression(current);
                            expression[0]=formatCalc(result);
                            // After equals, show only the answer.
                            display.setText(expression[0]);
                            justEvaluated[0]=true;
                        } catch(Exception e){
                            display.setText(tx("Error"));
                            expression[0]="";
                            justEvaluated[0]=false;
                        }
                    }
                }
            });
        }

        TextView hint=label("Enter numbers and +  −  ×  ÷",12,false);
        hint.setTextColor(TEXT_MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0,dp(8),0,0);
        panel.addView(hint,lp(-1,28));

        final AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(tx("Calculator"))
            .setView(panel)
            .setNegativeButton(tx("CLOSE"),null)
            .create();
        dialog.show();
    }

    // Evaluates +, −, × and ÷ with normal multiplication/division precedence.
    private double evaluateExpression(String text) throws Exception {
        ArrayList<Double> values=new ArrayList<Double>();
        ArrayList<Character> operators=new ArrayList<Character>();
        int i=0;
        boolean expectNumber=true;

        while(i<text.length()){
            int start=i;
            if(text.charAt(i)=='−' && expectNumber){ i++; }
            while(i<text.length() && (Character.isDigit(text.charAt(i))||text.charAt(i)=='.')) i++;
            if(start==i || (i==start+1 && text.charAt(start)=='−')) throw new Exception("Invalid number");
            double number=Double.parseDouble(text.substring(start,i));
            values.add(number);
            expectNumber=false;

            if(i<text.length()){
                char op=text.charAt(i++);
                if(op!='+'&&op!='−'&&op!='×'&&op!='÷') throw new Exception("Invalid operator");
                while(!operators.isEmpty() && precedence(operators.get(operators.size()-1))>=precedence(op)){
                    applyTop(values,operators);
                }
                operators.add(op);
                expectNumber=true;
            }
        }
        if(expectNumber) throw new Exception("Incomplete expression");
        while(!operators.isEmpty()) applyTop(values,operators);
        if(values.size()!=1) throw new Exception("Invalid expression");
        return values.get(0);
    }

    private int precedence(char op){
        return (op=='×'||op=='÷')?2:1;
    }

    private void applyTop(ArrayList<Double> values,ArrayList<Character> operators) throws Exception {
        if(values.size()<2||operators.isEmpty()) throw new Exception("Invalid expression");
        char op=operators.remove(operators.size()-1);
        double b=values.remove(values.size()-1);
        double a=values.remove(values.size()-1);
        if(op=='÷' && Math.abs(b)<0.0000000001) throw new Exception("Divide by zero");
        double result;
        if(op=='+') result=a+b;
        else if(op=='−') result=a-b;
        else if(op=='×') result=a*b;
        else result=a/b;
        values.add(result);
    }
    private String formatCalc(double v){
        if(Double.isNaN(v)||Double.isInfinite(v)) return "Error";
        if(Math.abs(v-Math.rint(v))<0.0000000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.getDefault(),"%.10f",v).replaceAll("0+$","").replaceAll("\\.$","");
    }



    private void buildInventory() {
        LinearLayout page=col(); page.setBackgroundColor(PAGE_BG); page.setPadding(dp(14),dp(12),dp(14),dp(10));
        TextView inventoryHeading=label("CURRENT INVENTORY",18,true);
        inventoryHeading.setPadding(dp(4),dp(2),dp(4),dp(6));
        LinearLayout.LayoutParams headingLp=lp(-1,-2);
        headingLp.setMargins(0,0,0,dp(4));
        page.addView(inventoryHeading,headingLp);
        inventoryList=col();
        ScrollView listScroll=new ScrollView(this); listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(true);
        // Keep a small, even inset around the inventory cards while scrolling.
        inventoryList.setPadding(0,dp(4),0,dp(4));
        inventoryList.setClipToPadding(false);
        listScroll.addView(inventoryList);
        page.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));

        // Fixed total uses the same left-product/right-quantity alignment as the list.
        inventoryTotal=col();
        styleCard(inventoryTotal, FIELD_BG);
        inventoryTotal.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout.LayoutParams totalLp=lp(-1,-2);
        totalLp.setMargins(0,dp(8),0,dp(8));
        page.addView(inventoryTotal,totalLp);

        LinearLayout bottom=row(); bottom.setGravity(Gravity.CENTER);
        Button beginning=btn("▣  BEGINNING INVENTORY"), ending=btn("✓  ENDING INVENTORY");
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(0,dp(48),1); blp.setMargins(0,0,dp(4),0);
        LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(0,dp(48),1); elp.setMargins(dp(4),0,0,0);
        styleUniformActionButton(beginning); styleUniformActionButton(ending);
        bottom.addView(beginning,blp); bottom.addView(ending,elp);
        LinearLayout.LayoutParams bottomLp=lp(-1,56);
        bottomLp.setMargins(0,0,0,0);
        page.addView(bottom,bottomLp);
        beginning.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTab(5);}});
        ending.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTab(6);}});

        Button withdraw=btn("↘  WITHDRAWAL");
        styleUniformActionButton(withdraw);
        LinearLayout.LayoutParams wp=lp(-1,48);
        wp.setMargins(0,dp(2),0,dp(4));
        page.addView(withdraw,wp);
        withdraw.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTab(7);}});
        host.addView(page,lp(-1,-1)); refreshInventory();
    }
    private void refreshInventory() {
        if(inventoryList==null)return;
        inventoryList.removeAllViews();
        double tb=0,tr=0,totalAmount=0;
        if(names.size()==0) inventoryList.addView(label("No current stocks yet. Add products in Beginning Inventory.",15,false));
        for(int i=0;i<names.size();i++) {
            tb+=boxes.get(i); tr+=reams.get(i);
            double price=(i<productPrices.size()?productPrices.get(i):1420.0);
            double productAmount=(boxes.get(i)*50.0+reams.get(i))*price;
            totalAmount+=productAmount;
            final String stockName=names.get(i);

            // Match Sales List: product/total label on the left, quantities/amount on the right.
            LinearLayout stockCard=col();
            stockCard.setPadding(dp(12),dp(10),dp(12),dp(10));
            styleCard(stockCard,CARD_BG);

            LinearLayout productLine=row();
            productLine.setGravity(Gravity.CENTER_VERTICAL);
            TextView product=label(stockName,14,true);
            product.setTextColor(TEXT_MAIN);
            // Display current stock with each complete 50 reams carried into boxes.
            double currentReams=reams.get(i);
            double currentExtraBoxes=Math.floor((currentReams+0.0000001d)/50.0d);
            double currentDisplayBoxes=boxes.get(i)+currentExtraBoxes;
            double currentDisplayReams=currentReams-(currentExtraBoxes*50.0d);
            if(Math.abs(currentDisplayReams)<0.0000001d) currentDisplayReams=0;
            TextView quantities=label("Box: "+qtyText(currentDisplayBoxes)+"  |  Ream: "+qtyText(currentDisplayReams),12,true);
            quantities.setTextColor(TEXT_MAIN);
            quantities.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            productLine.addView(product,new LinearLayout.LayoutParams(0,-2,1));
            productLine.addView(quantities,new LinearLayout.LayoutParams(-2,-2));
            stockCard.addView(productLine,lp(-1,-2));

            {
                LinearLayout amountLine=row();
                amountLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView amountLabel=label("AMOUNT",12,true);
                amountLabel.setTextColor(TEXT_MUTED);
                TextView amountValue=label(moneyText(productAmount),12,true);
                styleAmountValue(amountValue);
                amountLine.addView(amountLabel,new LinearLayout.LayoutParams(0,-2,1));
                amountLine.addView(amountValue,new LinearLayout.LayoutParams(-2,-2));
                LinearLayout.LayoutParams amountLp=lp(-1,-2);
                amountLp.setMargins(0,dp(5),0,0);
                stockCard.addView(amountLine,amountLp);
            }

            LinearLayout.LayoutParams rowLp=lp(-1,-2);
            rowLp.setMargins(0,dp(3),0,dp(3));
            inventoryList.addView(stockCard,rowLp);
            stockCard.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){editProduct(stockName);return true;}});
        }
        // Convert complete groups of 50 reams in the Current Inventory TOTAL display.
        double currentTotalExtraBoxes=Math.floor((tr+0.0000001d)/50.0d);
        double currentTotalDisplayBoxes=tb+currentTotalExtraBoxes;
        double currentTotalDisplayReams=tr-(currentTotalExtraBoxes*50.0d);
        if(Math.abs(currentTotalDisplayReams)<0.0000001d) currentTotalDisplayReams=0;
        if(inventoryTotal!=null) {
            inventoryTotal.removeAllViews();
            LinearLayout totalRow=row();
            totalRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView totalLabel=label("TOTAL",16,true);
            totalLabel.setTextColor(TEXT_MUTED);
            totalLabel.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            TextView totalQuantities=label("Box: "+qtyText(currentTotalDisplayBoxes)+"  |  Ream: "+qtyText(currentTotalDisplayReams),13,true);
            totalQuantities.setTextColor(TEXT_MAIN);
            totalQuantities.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            totalRow.addView(totalLabel,new LinearLayout.LayoutParams(0,dp(42),1));
            totalRow.addView(totalQuantities,new LinearLayout.LayoutParams(-2,dp(42)));
            inventoryTotal.addView(totalRow,lp(-1,-2));
            {
                View divider=new View(MainActivity.this);
                divider.setBackgroundColor(BORDER);
                LinearLayout.LayoutParams dividerLp=lp(-1,1);
                dividerLp.setMargins(0,dp(5),0,dp(5));
                inventoryTotal.addView(divider,dividerLp);
                LinearLayout amountTotalRow=row();
                amountTotalRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView amountTotalLabel=label("TOTAL AMOUNT",12,true);
                amountTotalLabel.setTextColor(TEXT_MUTED);
                amountTotalLabel.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                TextView amountTotalValue=label(moneyText(totalAmount),12,true);
                styleAmountValue(amountTotalValue);
                amountTotalRow.addView(amountTotalLabel,new LinearLayout.LayoutParams(0,dp(30),1));
                amountTotalRow.addView(amountTotalValue,new LinearLayout.LayoutParams(-2,dp(30)));
                inventoryTotal.addView(amountTotalRow,lp(-1,-2));
            }
        }
    }

    private void buildStockPage(final String mode) {
        LinearLayout page=col(); page.setBackgroundColor(PAGE_BG); page.setPadding(dp(6),dp(10),dp(6),dp(10));
        TextView stockPageHeading=label(mode.equals("STOCK IN")?"WITHDRAWAL":mode,18,true); stockPageHeading.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        stockPageHeading.setPadding(dp(4),dp(2),dp(4),dp(6));
        page.addView(stockPageHeading,lp(-1,-2));
        if(mode.equals("BEGINNING INVENTORY")) {
            final TextView dateButton=label("Beginning date: "+beginningDate+"   (tap to change)",15,true);
            dateButton.setTextColor(TEXT_MAIN); dateButton.setBackgroundColor(FIELD_BG); dateButton.setGravity(Gravity.CENTER); dateButton.setPadding(dp(4),dp(10),dp(4),dp(10));
            LinearLayout.LayoutParams dateLp=lp(-1,-2); dateLp.setMargins(dp(2),dp(6),dp(2),dp(6)); page.addView(dateButton,dateLp);
            dateButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                Calendar c=Calendar.getInstance(); try{c.setTime(new SimpleDateFormat("dd-MMM-yyyy",Locale.US).parse(beginningDate));}catch(Exception ex){}
                showThemedDatePicker(new DatePickerDialog(MainActivity.this, (lightTheme ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert), new DatePickerDialog.OnDateSetListener(){public void onDateSet(DatePicker view,int y,int m,int d){
                    Calendar picked=Calendar.getInstance();picked.set(y,m,d);beginningDate=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).format(picked.getTime());
                    boolean hasSnapshot=loadBeginningSnapshot(beginningDate);
                    if(!hasSnapshot) {
                        for(int i=0;i<names.size();i++) {
                            while(beginningBoxes.size()<=i) beginningBoxes.add(0.0);
                            while(beginningReams.size()<=i) beginningReams.add(0.0);
                            beginningBoxes.set(i,0.0); beginningReams.set(i,0.0);
                        }
                        Toast.makeText(MainActivity.this,tx("No saved Beginning Inventory for ")+beginningDate+". Quantities shown as 0 until saved.",Toast.LENGTH_LONG).show();
                    }
                    save();showTab(currentTab);
                }},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)));
            }});
        }
        else if(mode.equals("ENDING INVENTORY")) {
            final TextView dateButton=label("Ending date: "+endingDate+"   (tap to change)",15,true);
            dateButton.setTextColor(TEXT_MAIN); dateButton.setBackgroundColor(FIELD_BG); dateButton.setGravity(Gravity.CENTER); dateButton.setPadding(dp(4),dp(10),dp(4),dp(10));
            LinearLayout.LayoutParams dateLp=lp(-1,-2); dateLp.setMargins(dp(2),dp(6),dp(2),dp(6)); page.addView(dateButton,dateLp);
            dateButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                Calendar c=Calendar.getInstance(); try{c.setTime(new SimpleDateFormat("dd-MMM-yyyy",Locale.US).parse(endingDate));}catch(Exception ex){}
                showThemedDatePicker(new DatePickerDialog(MainActivity.this, (lightTheme ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert), new DatePickerDialog.OnDateSetListener(){public void onDateSet(DatePicker view,int y,int m,int d){
                    Calendar picked=Calendar.getInstance();picked.set(y,m,d);endingDate=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).format(picked.getTime());save();showTab(currentTab);
                }},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)));
            }});
        }
        else if(mode.equals("STOCK IN")) {
            if(withdrawViewDate==null||withdrawViewDate.length()==0) withdrawViewDate=today();
            final TextView dateButton=label("Withdrawal date: "+withdrawViewDate+"   (tap to change)",15,true);
            dateButton.setTextColor(TEXT_MAIN); dateButton.setBackgroundColor(FIELD_BG); dateButton.setGravity(Gravity.CENTER); dateButton.setPadding(dp(4),dp(10),dp(4),dp(10));
            LinearLayout.LayoutParams dateLp=lp(-1,-2); dateLp.setMargins(dp(2),dp(6),dp(2),dp(6)); page.addView(dateButton,dateLp);
            dateButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                Calendar c=Calendar.getInstance(); try{c.setTime(new SimpleDateFormat("dd-MMM-yyyy",Locale.US).parse(withdrawViewDate));}catch(Exception ex){}
                showThemedDatePicker(new DatePickerDialog(MainActivity.this, (lightTheme ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert), new DatePickerDialog.OnDateSetListener(){public void onDateSet(DatePicker view,int y,int m,int d){
                    Calendar picked=Calendar.getInstance();picked.set(y,m,d);withdrawViewDate=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).format(picked.getTime());save();showTab(currentTab);
                }},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)));
            }});
        }
        stockPageList=col(); ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.addView(stockPageList);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        if(mode.equals("BEGINNING INVENTORY") && names.isEmpty()) {
            TextView emptyHint=label("No products yet. Tap ADD PRODUCTS when you are ready to add your first product.",15,false);
            emptyHint.setTextColor(TEXT_MUTED); emptyHint.setPadding(dp(8),dp(12),dp(8),dp(12));
            stockPageList.addView(emptyHint,lp(-1,-2));
        }
        stockPageTotal=null;
        if(mode.equals("BEGINNING INVENTORY") || mode.equals("ENDING INVENTORY") || mode.equals("STOCK IN")) {
            stockPageTotal=col();
            styleCard(stockPageTotal, FIELD_BG);
            stockPageTotal.setPadding(dp(12),dp(8),dp(12),dp(8));
            LinearLayout.LayoutParams totalLp=lp(-1,-2); totalLp.setMargins(0,dp(6),0,dp(2));
            page.addView(stockPageTotal,totalLp);
        }
        if(mode.equals("STOCK IN")) {
            LinearLayout actionRow=row(); actionRow.setGravity(Gravity.CENTER_VERTICAL);
            Button add=btn("＋  ADD WITHDRAWAL"); styleUniformActionButton(add);
            Button change=btn("⇄  CHANGE STOCK"); styleUniformActionButton(change);
            LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(0,dp(50),1f); actionLp.setMargins(0,dp(8),dp(4),dp(4)); actionRow.addView(add,actionLp);
            LinearLayout.LayoutParams changeLp=new LinearLayout.LayoutParams(0,dp(50),1f); changeLp.setMargins(dp(4),dp(8),0,dp(4)); actionRow.addView(change,changeLp);
            page.addView(actionRow,lp(-1,-2));
            add.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showStockDialog(mode);}});
            change.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showChangeStockDialog();}});
        } else if(mode.equals("ENDING INVENTORY")) {
            LinearLayout endingActionRow=row();
            endingActionRow.setGravity(Gravity.CENTER_VERTICAL);
            Button saveEnding=btn("✓  SAVE");
            Button cutOffEnding=btn("◷  CUT-OFF");
            styleUniformActionButton(saveEnding);
            styleUniformActionButton(cutOffEnding);
            LinearLayout.LayoutParams saveActionLp=new LinearLayout.LayoutParams(0,dp(50),1f);
            saveActionLp.setMargins(0,dp(8),dp(4),dp(4));
            LinearLayout.LayoutParams cutOffActionLp=new LinearLayout.LayoutParams(0,dp(50),1f);
            cutOffActionLp.setMargins(dp(4),dp(8),0,dp(4));
            endingActionRow.addView(saveEnding,saveActionLp);
            endingActionRow.addView(cutOffEnding,cutOffActionLp);
            page.addView(endingActionRow,lp(-1,-2));
            cutOffEnding.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                new ThemedDialogBuilder()
                    .setTitle(tx("Confirm Sales Cut-Off"))
                    .setMessage("Itakda ang "+endingDate+" bilang huling araw ng sales period? Ang mga araw sa simula ng susunod na buwan bago ang unang Monday ay isasama sa unang linggo na nagsisimula sa Monday.")
                    .setNegativeButton(tx("Cancel"),null)
                    .setPositiveButton(tx("Confirm Cut-Off"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
                        salesCutoffDate=endingDate;
                        save();
                        Toast.makeText(MainActivity.this,tx("Sales cut-off set to ")+salesCutoffDate+". The next month's short days will roll into its first Monday-based week.",Toast.LENGTH_LONG).show();
                    }})
                    .show();
            }});
            saveEnding.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                new ThemedDialogBuilder()
                    .setTitle(tx("Confirm Ending Inventory"))
                    .setMessage("Sigurado ka bang ise-save ang Ending Inventory para sa "+endingDate+"?\n\nAng quantities na ito ay magiging bagong Beginning Inventory. Kung hindi ka pa tapos sa work period, piliin ang Cancel.")
                    .setNegativeButton(tx("Cancel"),null)
                    .setPositiveButton(tx("Confirm Save"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
                        // Save a complete snapshot for this ending date without touching older dates.
                        ArrayList<String> selected = new ArrayList<String>();
                        for(String rec : endingRecords) if(sameInventoryDate(recordDateOf(rec),endingDate)) selected.add(rec);
                        for(int i=0;i<names.size();i++) {
                            String product=names.get(i); String match=null;
                            for(String rec:selected) if(recordProductName(rec).equalsIgnoreCase(product)) { match=rec; break; }
                            double b=match==null?0:quantityFromRecord(match,"Box:","|");
                            double r=match==null?0:quantityFromRecord(match,"Ream:",null);
                            boxes.set(i,b); reams.set(i,r);
                            while(beginningBoxes.size()<=i) beginningBoxes.add(0.0);
                            while(beginningReams.size()<=i) beginningReams.add(0.0);
                            beginningBoxes.set(i,b); beginningReams.set(i,r);
                        }
                        removeInventoryDate(endingRecords,endingDate);
                        for(int i=0;i<names.size();i++) endingRecords.add("Date: "+endingDate+"\n"+names.get(i)+"\nBox: "+qtyText(boxes.get(i))+"  |  Ream: "+qtyText(reams.get(i)));
                        beginningDate=endingDate;
                        saveBeginningSnapshot(beginningDate);
                        save(); Toast.makeText(MainActivity.this,tx("Ending inventory saved. It is now the new Beginning Inventory."),Toast.LENGTH_LONG).show(); showTab(currentTab);
                    }})
                    .show();
            }});
        } else {
            if(mode.equals("BEGINNING INVENTORY")) {
                LinearLayout actions=row(); actions.setGravity(Gravity.CENTER_VERTICAL);
                Button add=btn("＋  ADD PRODUCTS"); Button saveBeginning=btn("✓  SAVE BEGINNING");
                LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(0,dp(50),1f);addLp.setMargins(0,dp(8),dp(4),dp(4));actions.addView(add,addLp);
                LinearLayout.LayoutParams saveLp=new LinearLayout.LayoutParams(0,dp(50),1f);saveLp.setMargins(dp(4),dp(8),0,dp(4));actions.addView(saveBeginning,saveLp);
                page.addView(actions,lp(-1,-2));
                add.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showStockDialog(mode);}});
                saveBeginning.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                    saveBeginningSnapshot(beginningDate); save();
                    Toast.makeText(MainActivity.this,tx("Beginning Inventory saved for ")+beginningDate,Toast.LENGTH_LONG).show();
                    showTab(currentTab);
                }});
            } else {
                Button add=btn("＋  ADD PRODUCTS");
                LinearLayout.LayoutParams ap=lp(-1,50);ap.setMargins(0,dp(8),0,dp(4));page.addView(add,ap);
                add.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showStockDialog(mode);}});
            }
        }
        Button back=btn("‹  BACK");LinearLayout.LayoutParams bp=lp(-1,48);bp.setMargins(0,dp(4),0,0);page.addView(back,bp);
        back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTab(0);}});
        host.addView(page,lp(-1,-1)); refreshStockPage(mode); updateStockPageTotal(mode);
    }

    private String recordDateOf(String record) {
        if(record==null || !record.startsWith("Date:")) return "";
        int end=record.indexOf('\n');
        return record.substring(5,end>=0?end:record.length()).trim();
    }
    private void removeInventoryDate(ArrayList<String> list,String date) {
        for(int i=list.size()-1;i>=0;i--) if(sameInventoryDate(recordDateOf(list.get(i)),date)) list.remove(i);
    }
    private void saveBeginningSnapshot(String date) {
        removeInventoryDate(beginningHistory,date);
        for(int i=0;i<names.size();i++) {
            double b=i<beginningBoxes.size()?beginningBoxes.get(i):0;
            double r=i<beginningReams.size()?beginningReams.get(i):0;
            beginningHistory.add("Date: "+date+"\n"+names.get(i)+"\nBox: "+qtyText(b)+"  |  Ream: "+qtyText(r));
        }
    }
    private boolean loadBeginningSnapshot(String date) {
        ArrayList<String> matches=new ArrayList<String>();
        for(String rec:beginningHistory) if(sameInventoryDate(recordDateOf(rec),date)) matches.add(rec);
        if(matches.size()==0) return false;
        for(int i=0;i<names.size();i++) {
            boolean found=false;
            for(String rec:matches) if(recordProductName(rec).equalsIgnoreCase(names.get(i))) {
                while(beginningBoxes.size()<=i) beginningBoxes.add(0.0);
                while(beginningReams.size()<=i) beginningReams.add(0.0);
                beginningBoxes.set(i,quantityFromRecord(rec,"Box:","|"));
                beginningReams.set(i,quantityFromRecord(rec,"Ream:",null));
                found=true; break;
            }
            if(!found) {
                while(beginningBoxes.size()<=i) beginningBoxes.add(0.0);
                while(beginningReams.size()<=i) beginningReams.add(0.0);
                beginningBoxes.set(i,0.0); beginningReams.set(i,0.0);
            }
        }
        return true;
    }
    private double quantityFromRecord(String record, String key, String endKey) {
        try {
            int start=record.indexOf(key);
            if(start<0)return 0.0;
            start+=key.length();
            int end=endKey==null?-1:record.indexOf(endKey,start);
            String value=end<0?record.substring(start):record.substring(start,end);
            return qty(value.trim());
        } catch(Exception e) { return 0.0; }
    }

    private void updateStockPageTotal(String mode) {
        if(stockPageTotal==null)return;
        double totalBox=0,totalReam=0,totalAmount=0;
        if(mode.equals("BEGINNING INVENTORY")) {
            for(int i=0;i<names.size();i++){
                double b=i<beginningBoxes.size()?beginningBoxes.get(i):boxes.get(i);
                double r=i<beginningReams.size()?beginningReams.get(i):reams.get(i);
                totalBox+=b; totalReam+=r;
                double price=i<productPrices.size()?productPrices.get(i):1420.0;
                totalAmount+=(b*50.0+r)*price;
            }
        } else if(mode.equals("ENDING INVENTORY")) {
            // Only total the saved records for the currently selected ending date.
            for(int i=0;i<endingRecords.size();i++) {
                String record=endingRecords.get(i);
                if(!sameInventoryDate(recordDateOf(record),endingDate)) continue;
                double b=quantityFromRecord(record,"Box:","|");
                double r=quantityFromRecord(record,"Ream:",null);
                totalBox+=b; totalReam+=r;
                String productName=recordProductName(record);
                int idx=find(productName); double price=idx>=0&&idx<productPrices.size()?productPrices.get(idx):1420.0;
                totalAmount+=(b*50.0+r)*price;
            }
        } else if(mode.equals("STOCK IN")) {
            // Show the value of Stock In records for the selected date, not current stock.
            for(int i=0;i<withdrawRecords.size();i++){
                String record=withdrawRecords.get(i);
                if(!sameInventoryDate(recordDateOf(record),withdrawViewDate)) continue;
                double b=quantityFromRecord(record,"Box:","|");
                double r=quantityFromRecord(record,"Ream:",null);
                totalBox+=b; totalReam+=r;
                String productName=recordProductName(record);
                int idx=find(productName); double price=idx>=0&&idx<productPrices.size()?productPrices.get(idx):1420.0;
                totalAmount+=(b*50.0+r)*price;
            }
        }
        // Convert every complete group of 50 reams into one box for the TOTAL display only.
        double extraBoxes=Math.floor((totalReam+0.0000001)/50.0);
        totalBox+=extraBoxes;
        totalReam-=extraBoxes*50.0;
        if(totalReam<0.0000001) totalReam=0;

        stockPageTotal.removeAllViews();
        LinearLayout totalRow=row(); totalRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView totalLabel=label("TOTAL",16,true); totalLabel.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        totalRow.addView(totalLabel,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView totalQuantities=label("Box: "+qtyText(totalBox)+"  |  Ream: "+qtyText(totalReam),13,true);
        totalQuantities.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        totalRow.addView(totalQuantities,new LinearLayout.LayoutParams(-2,dp(42)));
        stockPageTotal.addView(totalRow,lp(-1,-2));
        View divider=new View(this); divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dividerLp=lp(-1,1); dividerLp.setMargins(0,dp(5),0,dp(5));
        stockPageTotal.addView(divider,dividerLp);
        LinearLayout amountRow=row(); amountRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView amountLabel=label("TOTAL AMOUNT",12,true); amountLabel.setTextColor(TEXT_MUTED);
        TextView amountValue=label(moneyText(totalAmount),12,true); styleAmountValue(amountValue);
        amountRow.addView(amountLabel,new LinearLayout.LayoutParams(0,dp(30),1));
        amountRow.addView(amountValue,new LinearLayout.LayoutParams(-2,-2));
        stockPageTotal.addView(amountRow,lp(-1,-2));
    }

    private void showStockDialog(final String mode) {
        final LinearLayout form=col();form.setPadding(dp(12),dp(8),dp(12),dp(8));form.setBackgroundColor(CARD_BG);
        final ArrayList<String> productOptions=new ArrayList<String>();
        productOptions.add("— Select product —");
        for(int i=0;i<names.size();i++) productOptions.add(names.get(i));
        final Spinner name=new Spinner(this);
        ArrayAdapter<String> productAdapter=themedSpinnerAdapter(productOptions);
        name.setAdapter(productAdapter);
        name.setBackgroundColor(FIELD_BG);name.setPadding(dp(8),0,dp(8),0);
        if(android.os.Build.VERSION.SDK_INT>=16) name.setPopupBackgroundDrawable(new ColorDrawable(FIELD_BG));
        final EditText newProductName=edit("Enter product name",false);
        capitalizeFirstInputLetter(newProductName);
        newProductName.setVisibility(mode.equals("BEGINNING INVENTORY")?View.VISIBLE:View.GONE);
        name.setVisibility(mode.equals("BEGINNING INVENTORY")?View.GONE:View.VISIBLE);
        final EditText box=edit("0",true), ream=edit("0",true);
        final String[] recordDate={mode.equals("ENDING INVENTORY")?endingDate:(mode.equals("STOCK IN")?(withdrawViewDate==null||withdrawViewDate.length()==0?today():withdrawViewDate):today())};
        if(!mode.equals("BEGINNING INVENTORY") && !mode.equals("ENDING INVENTORY") && !mode.equals("STOCK IN")) {
            final TextView dateButton=label("Date: "+recordDate[0]+"   (tap to change)",15,true);
            dateButton.setBackgroundColor(FIELD_BG); dateButton.setPadding(dp(10),dp(10),dp(10),dp(10));
            form.addView(label("Record date",14,true));form.addView(dateButton,lp(-1,46));
            dateButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                Calendar c=Calendar.getInstance();try{c.setTime(new SimpleDateFormat("dd-MMM-yyyy",Locale.US).parse(recordDate[0]));}catch(Exception ex){}
                showThemedDatePicker(new DatePickerDialog(MainActivity.this, (lightTheme ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert), new DatePickerDialog.OnDateSetListener(){public void onDateSet(DatePicker view,int y,int m,int d){
                    Calendar picked=Calendar.getInstance();picked.set(y,m,d);recordDate[0]=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).format(picked.getTime());dateButton.setText(tx("Date: ")+recordDate[0]+"   (tap to change)");
                }},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)));
            }});
        }
        if(mode.equals("BEGINNING INVENTORY")) {
            form.addView(label("Product name",14,true));
            form.addView(newProductName,lp(-1,48));
        } else {
            form.addView(label("Select product",14,true));
            form.addView(name,lp(-1,48));
        }
        form.addView(sectionDivider());
        form.addView(label("Box Qty.",14,true));form.addView(box,lp(-1,48));
        form.addView(label("Ream Qty.",14,true));form.addView(ream,lp(-1,48));
        final AlertDialog dialog=new ThemedDialogBuilder().setTitle(mode).setView(form).setNegativeButton(tx("Cancel"),null).setPositiveButton(tx("Save"),null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){
            applyDialogTheme(dialog);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(TEXT_MAIN);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_MAIN);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                int selectedPosition=name.getSelectedItemPosition();
                String n="";
                if(mode.equals("BEGINNING INVENTORY")) {
                    n=newProductName.getText().toString().trim();
                    if(n.length()==0){newProductName.setError("Enter product name");return;}
                    if(find(n)>=0){newProductName.setError("Product already exists");return;}
                } else {
                    if(selectedPosition>0 && selectedPosition<productOptions.size()) n=String.valueOf(productOptions.get(selectedPosition)).trim();
                    if(n.length()==0 || n.equals("— Select product —")){Toast.makeText(MainActivity.this,tx("Please select a product"),Toast.LENGTH_SHORT).show();return;}
                }
                double b,r;try{b=qty(box.getText().toString());r=qty(ream.getText().toString());}catch(Exception ex){Toast.makeText(MainActivity.this,tx("Enter valid non-negative quantities"),Toast.LENGTH_LONG).show();return;}
                int idx=find(n);
                if(mode.equals("BEGINNING INVENTORY")) {
                    if(idx<0){names.add(n);boxes.add(b);reams.add(r);productPrices.add(1420.0);beginningBoxes.add(b);beginningReams.add(r);} else {while(beginningBoxes.size()<=idx)beginningBoxes.add(boxes.get(beginningBoxes.size()));while(beginningReams.size()<=idx)beginningReams.add(reams.get(beginningReams.size()));boxes.set(idx,b);reams.set(idx,r);beginningBoxes.set(idx,b);beginningReams.set(idx,r);}
                } else if(mode.equals("ENDING INVENTORY")) {
                    endingRecords.add("Date: "+endingDate+"\n"+n+"\nBox: "+qtyText(b)+"  |  Ream: "+qtyText(r));
                    // A saved ending count becomes the new live/beginning stock for
                    // the next inventory period, replacing the older starting count.
                    if(idx>=0) {
                        while(beginningBoxes.size()<=idx) beginningBoxes.add(boxes.get(beginningBoxes.size()));
                        while(beginningReams.size()<=idx) beginningReams.add(reams.get(beginningReams.size()));
                        beginningBoxes.set(idx,b);
                        beginningReams.set(idx,r);
                        boxes.set(idx,b);
                        reams.set(idx,r);
                    }
                    beginningDate=endingDate;
                } else {
                    if(idx<0){Toast.makeText(MainActivity.this,tx("Add this product in Beginning Inventory first"),Toast.LENGTH_LONG).show();return;}
                    // A Stock In addition increases both the week's tracked beginning total
                    // and the live current stock. Sales only reduce current stock.
                    while(beginningBoxes.size()<=idx) beginningBoxes.add(boxes.get(beginningBoxes.size()));
                    while(beginningReams.size()<=idx) beginningReams.add(reams.get(beginningReams.size()));
                    beginningBoxes.set(idx,beginningBoxes.get(idx)+b);
                    beginningReams.set(idx,beginningReams.get(idx)+r);
                    boxes.set(idx,boxes.get(idx)+b);
                    reams.set(idx,reams.get(idx)+r);
                    withdrawRecords.add("Date: "+recordDate[0]+"\n"+n+"\nBox: "+qtyText(b)+"  |  Ream: "+qtyText(r));
                }
                save();dialog.dismiss();showTab(currentTab);
            }});
        }});dialog.show();
    }
    private void showChangeStockDialog() {
        if(names.size()<2) { Toast.makeText(this,tx("You need at least two products to change stock"),Toast.LENGTH_LONG).show(); return; }
        final LinearLayout form=col(); form.setPadding(dp(12),dp(8),dp(12),dp(8)); form.setBackgroundColor(CARD_BG);
        final AutoCompleteTextView customerName=new AutoCompleteTextView(this);
        customerName.setSingleLine(true);
        customerName.setTextColor(TEXT_MAIN);
        customerName.setHintTextColor(TEXT_MUTED);
        customerName.setHint(tx("Enter customer name"));
        customerName.setTextSize(TypedValue.COMPLEX_UNIT_SP,16);
        customerName.setPadding(dp(12),0,dp(12),0);
        customerName.setBackground(rounded(FIELD_BG,10,BORDER));
        customerName.setThreshold(1);
        customerName.setDropDownBackgroundDrawable(new ColorDrawable(CARD_BG));
        final ArrayList<String> rememberedCustomers=new ArrayList<String>();
        final HashSet<String> seenCustomerNames=new HashSet<String>();
        for(Sale oldSale:sales){
            String remembered=oldSale.customer==null?"":oldSale.customer.trim();
            if(remembered.length()==0 || remembered.toLowerCase(Locale.US).startsWith("Cash-") )continue;
            String key=remembered.toLowerCase(Locale.US);
            if(seenCustomerNames.add(key))rememberedCustomers.add(remembered);
        }
        ArrayAdapter<String> customerAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,rememberedCustomers){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
                TextView t=(TextView)super.getView(position,convertView,parent);t.setTextColor(TEXT_MAIN);t.setBackgroundColor(CARD_BG);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;
            }
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){
                TextView t=(TextView)super.getDropDownView(position,convertView,parent);t.setTextColor(TEXT_MAIN);t.setBackgroundColor(CARD_BG);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;
            }
        };
        customerName.setAdapter(customerAdapter);
        form.addView(label("CUSTOMER NAME",13,true)); form.addView(customerName,lp(-1,46));
        final ArrayList<String> options=new ArrayList<String>(); options.add("— Select product —"); options.addAll(names);
        final Spinner from=new Spinner(this), to=new Spinner(this);
        from.setAdapter(themedSpinnerAdapter(options)); to.setAdapter(themedSpinnerAdapter(options));
        from.setBackgroundColor(FIELD_BG); to.setBackgroundColor(FIELD_BG); from.setPadding(dp(8),0,dp(8),0); to.setPadding(dp(8),0,dp(8),0);
        if(android.os.Build.VERSION.SDK_INT>=16){from.setPopupBackgroundDrawable(new ColorDrawable(FIELD_BG));to.setPopupBackgroundDrawable(new ColorDrawable(FIELD_BG));}
        form.addView(label("FROM",14,true)); form.addView(from,lp(-1,46));
        final EditText fromBox=edit("0",true), fromReam=edit("0",true);
        form.addView(label("BOX QUANTITY",13,true)); form.addView(fromBox,lp(-1,44));
        form.addView(label("REAM QUANTITY",13,true)); form.addView(fromReam,lp(-1,44));
        form.addView(sectionDivider());
        form.addView(label("TO",14,true)); form.addView(to,lp(-1,46));
        final EditText toBox=edit("0",true), toReam=edit("0",true);
        toBox.setTextColor(TEXT_MUTED); toReam.setTextColor(TEXT_MUTED); toBox.setFocusable(false); toReam.setFocusable(false); toBox.setClickable(false); toReam.setClickable(false);
        form.addView(label("BOX QUANTITY",13,true)); form.addView(toBox,lp(-1,44));
        form.addView(label("REAM QUANTITY",13,true)); form.addView(toReam,lp(-1,44));
        android.text.TextWatcher sync=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){} public void onTextChanged(CharSequence s,int start,int before,int count){toBox.setText(fromBox.getText().toString());toReam.setText(fromReam.getText().toString());} public void afterTextChanged(android.text.Editable e){}};
        fromBox.addTextChangedListener(sync); fromReam.addTextChangedListener(sync);
        final AlertDialog dialog=new ThemedDialogBuilder().setTitle(tx("CHANGE STOCK")).setView(form).setNegativeButton(tx("CANCEL"),null).setPositiveButton(tx("CHANGE"),null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){
            applyDialogTheme(dialog); dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(TEXT_MAIN); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_MAIN);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                String customer=customerName.getText().toString().trim();
                int fp=from.getSelectedItemPosition(), tp=to.getSelectedItemPosition();
                if(customer.length()==0){customerName.setError("Enter customer name");return;}
                if(fp<=0||tp<=0||fp>=options.size()||tp>=options.size()){Toast.makeText(MainActivity.this,tx("Select both FROM and TO products"),Toast.LENGTH_SHORT).show();return;}
                int fi=find(options.get(fp)), ti=find(options.get(tp));
                if(fi<0||ti<0){Toast.makeText(MainActivity.this,tx("Selected product was not found"),Toast.LENGTH_SHORT).show();return;}
                if(fi==ti){Toast.makeText(MainActivity.this,tx("FROM and TO products must be different"),Toast.LENGTH_SHORT).show();return;}
                double b,r; try{b=qty(fromBox.getText().toString());r=qty(fromReam.getText().toString());}catch(Exception ex){Toast.makeText(MainActivity.this,tx("Enter valid non-negative quantities"),Toast.LENGTH_SHORT).show();return;}
                if(b<=0&&r<=0){Toast.makeText(MainActivity.this,tx("Enter a box or ream quantity"),Toast.LENGTH_SHORT).show();return;}
                while(beginningBoxes.size()<=fi)beginningBoxes.add(boxes.get(beginningBoxes.size())); while(beginningReams.size()<=fi)beginningReams.add(reams.get(beginningReams.size()));
                while(beginningBoxes.size()<=ti)beginningBoxes.add(boxes.get(beginningBoxes.size())); while(beginningReams.size()<=ti)beginningReams.add(reams.get(beginningReams.size()));
                // Transfer equal quantities between products: the overall beginning and current totals stay unchanged.
                if(beginningBoxes.get(ti)<b || beginningReams.get(ti)<r || boxes.get(ti)<b || reams.get(ti)<r){Toast.makeText(MainActivity.this,tx("The TO product does not have enough stock to exchange that quantity"),Toast.LENGTH_LONG).show();return;}
                beginningBoxes.set(fi,beginningBoxes.get(fi)+b); beginningReams.set(fi,beginningReams.get(fi)+r); boxes.set(fi,boxes.get(fi)+b); reams.set(fi,reams.get(fi)+r);
                beginningBoxes.set(ti,beginningBoxes.get(ti)-b); beginningReams.set(ti,beginningReams.get(ti)-r); boxes.set(ti,boxes.get(ti)-b); reams.set(ti,reams.get(ti)-r);
                String fromName=names.get(fi), toName=names.get(ti);
                sales.add(0,new Sale(withdrawViewDate==null||withdrawViewDate.length()==0?today():withdrawViewDate,customer,"CHANGE_STOCK::"+fromName+"::"+toName+"::"+qtyText(b)+"::"+qtyText(r),0,0,0,0,1420.0));
                save(); dialog.dismiss(); showTab(currentTab); Toast.makeText(MainActivity.this,tx("Stock change recorded"),Toast.LENGTH_SHORT).show();
            }});
        }}); dialog.show();
    }

    private String recordProductName(String record) {
        if(record==null)return "";
        String[] lines=record.split("\n");
        for(String line:lines) {
            String t=line.trim();
            if(t.length()>0 && !t.startsWith("Date:") && !t.startsWith("Box:") && !t.startsWith("Ream:")) return t;
        }
        return "";
    }

    private void refreshStockPage(String mode) {
        if(stockPageList==null)return;
        stockPageList.removeAllViews();
        ArrayList<String> records;
        ArrayList<Integer> sourceIndices=new ArrayList<Integer>();
        if(mode.equals("ENDING INVENTORY")) { records=new ArrayList<String>(endingRecords); for(int i=0;i<endingRecords.size();i++)sourceIndices.add(i); }
        else if(mode.equals("STOCK IN")) { records=new ArrayList<String>(withdrawRecords); for(int i=0;i<records.size();i++)sourceIndices.add(i); }
        else {
            records=new ArrayList<String>();
            for(int i=0;i<names.size();i++) { records.add(names.get(i)+"\nBox: "+qtyText(i<beginningBoxes.size()?beginningBoxes.get(i):boxes.get(i))+"  |  Ream: "+qtyText(i<beginningReams.size()?beginningReams.get(i):reams.get(i))); sourceIndices.add(i); }
        }
        if(mode.equals("ENDING INVENTORY") || mode.equals("STOCK IN")) {
            final String filterDate=mode.equals("ENDING INVENTORY")?endingDate:withdrawViewDate;
            ArrayList<String> filteredRecords=new ArrayList<String>();
            ArrayList<Integer> filteredIndices=new ArrayList<Integer>();
            for(int j=0;j<records.size();j++) {
                String record=records.get(j);
                String savedDate = "";
                if (record.startsWith("Date:")) {
                    int lineEnd = record.indexOf('\n');
                    savedDate = record.substring(5, lineEnd >= 0 ? lineEnd : record.length()).trim();
                }
                // Compare calendar dates rather than raw strings so older records
                // remain visible even if the device locale formats month names differently.
                if (sameInventoryDate(savedDate, filterDate)) {
                    filteredRecords.add(record);
                    filteredIndices.add(sourceIndices.get(j));
                }
            }
            records=filteredRecords; sourceIndices=filteredIndices;
        }
        // Ending Inventory is a daily worksheet: always show every known product.
        // Saved counts for the selected date remain visible; products not yet counted
        // for that date appear with zero quantities without creating fake saved records.
        if(mode.equals("ENDING INVENTORY")) {
            java.util.HashSet<String> countedProducts = new java.util.HashSet<String>();
            for(String savedRecord : records) {
                String savedName = recordProductName(savedRecord);
                if(savedName.length()>0) countedProducts.add(savedName.toLowerCase(Locale.ROOT));
            }
            for(int productIndex=0; productIndex<names.size(); productIndex++) {
                String productName = names.get(productIndex);
                if(!countedProducts.contains(productName.toLowerCase(Locale.ROOT))) {
                    records.add("Date: "+endingDate+"\n"+productName+"\nBox: 0  |  Ream: 0");
                    sourceIndices.add(-1);
                }
            }
        }
        if(records.size()==0){TextView empty=label(mode.equals("STOCK IN")?"No Withdrawal records for this date.":"No records yet.",15,false);empty.setPadding(dp(12),dp(12),dp(12),dp(12));stockPageList.addView(empty);return;}
        // Product rows in Beginning Inventory follow insertion order (Product 1 first,
        // then Product 2 underneath, etc.). Activity records retain newest-first order.
        for(int i=0;i<records.size();i++) {
            int displayIndex=(mode.equals("BEGINNING INVENTORY") || mode.equals("ENDING INVENTORY"))?i:(records.size()-1-i);
            final int recordIndex=sourceIndices.get(displayIndex);
            final String record=records.get(displayIndex);
            String[] lines=record.split("\n");
            String dateText="";
            String productName="";
            String quantityLine="";
            for(String line:lines) {
                String trimmed=line.trim();
                if(trimmed.startsWith("Date:")) dateText=trimmed;
                else if(trimmed.startsWith("Box:")) quantityLine=trimmed;
                else if(trimmed.startsWith("Ream:")) quantityLine=(quantityLine.length()==0?"":quantityLine+"  |  ")+trimmed;
                else if(trimmed.length()>0) productName=trimmed;
            }
            double b=quantityFromRecord(quantityLine,"Box:","|");
            double r=quantityFromRecord(quantityLine,"Ream:",null);
            LinearLayout card=col();
            styleCard(card,i%2==0?CARD_BG:HEADER_BG);
            card.setPadding(dp(12),dp(8),dp(12),dp(8));
            LinearLayout contentRow=row(); contentRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left=col();
            TextView product=label(productName,15,true); product.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            left.addView(product,lp(-1,-2));
            if(dateText.length()>0 && !mode.equals("STOCK IN")) {TextView date=label(dateText,12,false);date.setTextColor(TEXT_MUTED);date.setPadding(0,dp(3),0,0);left.addView(date,lp(-1,-2));}
            contentRow.addView(left,new LinearLayout.LayoutParams(0,-2,1));
            TextView right=label("Box: "+qtyText(b)+"  |  Ream: "+qtyText(r),13,true);
            right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            right.setSingleLine(true);
            contentRow.addView(right,lp(174,-2));
            card.addView(contentRow,lp(-1,-2));
            if(mode.equals("BEGINNING INVENTORY") || mode.equals("ENDING INVENTORY") || mode.equals("STOCK IN")) {
                double amountBox=b, amountReam=r;
                if(mode.equals("BEGINNING INVENTORY") && recordIndex>=0 && recordIndex<names.size()) {
                    amountBox=recordIndex<beginningBoxes.size()?beginningBoxes.get(recordIndex):boxes.get(recordIndex);
                    amountReam=recordIndex<beginningReams.size()?beginningReams.get(recordIndex):reams.get(recordIndex);
                }
                String amountProduct=mode.equals("BEGINNING INVENTORY")?(recordIndex>=0&&recordIndex<names.size()?names.get(recordIndex):productName):productName;
                int priceIndex=find(amountProduct);
                double unitPrice=priceIndex>=0&&priceIndex<productPrices.size()?productPrices.get(priceIndex):1420.0;
                double amount=(amountBox*50.0+amountReam)*unitPrice;
                LinearLayout amountRow=row(); amountRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView amountLabel=label("AMOUNT",12,true); amountLabel.setTextColor(TEXT_MUTED);
                TextView amountValue=label(moneyText(amount),12,true); styleAmountValue(amountValue);
                amountRow.addView(amountLabel,new LinearLayout.LayoutParams(0,-2,1));
                amountRow.addView(amountValue,new LinearLayout.LayoutParams(-2,-2));
                LinearLayout.LayoutParams amountLp=lp(-1,-2); amountLp.setMargins(0,dp(5),0,0);
                card.addView(amountRow,amountLp);
            }
            LinearLayout.LayoutParams rowParams=lp(-1,-2);rowParams.setMargins(0,dp(4),0,dp(4));stockPageList.addView(card,rowParams);
            if (mode.equals("ENDING INVENTORY")) {
                // Tap an ending-inventory row to edit or delete it; long-press remains supported too.
                card.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { showRecordActions(mode, recordIndex); }
                });
            }
            card.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){if(recordIndex<0)return true;if(mode.equals("BEGINNING INVENTORY")){if(recordIndex<names.size())editProduct(names.get(recordIndex));}else showRecordActions(mode,recordIndex);return true;}});
        }
    }

    private void showRecordActions(final String mode,final int index){
        final ArrayList<String> list=mode.equals("ENDING INVENTORY")?endingRecords:withdrawRecords;
        if(index<0||index>=list.size())return;
        new ThemedDialogBuilder().setTitle(mode).setItems(new String[]{"Edit record","Delete record"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0)editRecordText(mode,index);else deleteRecord(mode,index);}}).setNegativeButton(tx("Cancel"),null).show();
    }
    private void editRecordText(final String mode,final int index){
        final ArrayList<String> list=mode.equals("ENDING INVENTORY")?endingRecords:withdrawRecords;
        if(index<0||index>=list.size())return;
        // Ending inventory uses the same clear, labeled fields as Edit Product / Stock.
        if(mode.equals("ENDING INVENTORY")){
            final String[] lines=list.get(index).split("\n");
            String date=today(), product="", boxValue="0", reamValue="0";
            for(int i=0;i<lines.length;i++){
                String line=lines[i].trim();
                if(line.startsWith("Date:"))date=line.substring(5).trim();
                else if(line.startsWith("Box:")){
                    String[] parts=line.split("\\|");
                    if(parts.length>0)boxValue=parts[0].replace("Box:","").trim();
                    if(parts.length>1)reamValue=parts[1].replace("Ream:","").trim();
                } else if(line.length()>0)product=line;
            }
            final LinearLayout form=col();form.setPadding(dp(12),dp(8),dp(12),dp(8));form.setBackgroundColor(CARD_BG);
            final EditText dateField=edit("dd-MMM-yyyy",false), name=edit("Product name",false), box=edit("0",true), ream=edit("0",true);
            capitalizeFirstInputLetter(name);
            dateField.setText(date);name.setText(product);box.setText(boxValue);ream.setText(reamValue);
            form.addView(label("Record date",14,true));form.addView(dateField,lp(-1,48));
            form.addView(sectionDivider());
            form.addView(label("Product name",14,true));form.addView(name,lp(-1,48));
            form.addView(label("Box quantity",14,true));form.addView(box,lp(-1,48));
            form.addView(label("Ream quantity",14,true));form.addView(ream,lp(-1,48));
            final AlertDialog dialog=new ThemedDialogBuilder().setTitle(tx("Edit ENDING INVENTORY")).setView(form).setNegativeButton(tx("Cancel"),null).setPositiveButton(tx("Save"),null).create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){
                applyDialogTheme(dialog);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));
                dialog.getButton(-1).setTextColor(TEXT_MAIN);dialog.getButton(-2).setTextColor(TEXT_MAIN);
                dialog.getButton(-1).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                    String n=name.getText().toString().trim(),dt=dateField.getText().toString().trim();
                    if(n.length()==0){name.setError("Enter product name");return;}
                    if(dt.length()==0){dateField.setError("Enter record date");return;}
                    try{double b=qty(box.getText().toString()),r=qty(ream.getText().toString());
                        list.set(index,"Date: "+dt+"\n"+n+"\nBox: "+qtyText(b)+"  |  Ream: "+qtyText(r));
                        if(mode.equals("ENDING INVENTORY")) {
                            int productIndex=find(n);
                            if(productIndex>=0) { boxes.set(productIndex,b); reams.set(productIndex,r); }
                        }
                        save();dialog.dismiss();showTab(currentTab);
                    }catch(Exception ex){Toast.makeText(MainActivity.this,tx("Enter valid non-negative quantities"),Toast.LENGTH_SHORT).show();}
                }});
            }});dialog.show();
        } else {
            // Structured withdrawal editor: edit date, product, box and ream separately.
            final String[] lines=list.get(index).split("\n");
            String oldDate=today(), oldProduct="", oldBox="0", oldReam="0";
            for(int i=0;i<lines.length;i++){
                String line=lines[i].trim();
                if(line.startsWith("Date:"))oldDate=line.substring(5).trim();
                else if(line.startsWith("Box:")){String[] parts=line.split("\\|");if(parts.length>0)oldBox=parts[0].replace("Box:","").trim();if(parts.length>1)oldReam=parts[1].replace("Ream:","").trim();}
                else if(line.length()>0)oldProduct=line;
            }
            // Copy parsed values into final variables before using them in click listeners.
            final String stockInOldBox=oldBox;
            final String stockInOldReam=oldReam;
            final int oldProductIndex=find(oldProduct);
            final LinearLayout form=col();form.setPadding(dp(12),dp(8),dp(12),dp(8));form.setBackgroundColor(CARD_BG);
            final String[] recordDate={oldDate};
            final TextView dateButton=label("📅  "+oldDate+"   (tap to change)",15,true);dateButton.setBackgroundColor(FIELD_BG);dateButton.setPadding(dp(10),dp(10),dp(10),dp(10));
            form.addView(label("Withdrawal date",14,true));form.addView(dateButton,lp(-1,48));
            dateButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){Calendar c=Calendar.getInstance();try{c.setTime(new SimpleDateFormat("dd-MMM-yyyy",Locale.US).parse(recordDate[0]));}catch(Exception ex){}showThemedDatePicker(new DatePickerDialog(MainActivity.this,(lightTheme?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert),new DatePickerDialog.OnDateSetListener(){public void onDateSet(DatePicker view,int y,int m,int d){Calendar picked=Calendar.getInstance();picked.set(y,m,d);recordDate[0]=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).format(picked.getTime());dateButton.setText(tx("📅  ")+recordDate[0]+"   (tap to change)");}},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)));}});
            form.addView(label("Product name",14,true));
            final EditText product=edit("Enter product name",false);capitalizeFirstInputLetter(product);product.setText(oldProduct);form.addView(product,lp(-1,48));
            form.addView(label("Box quantity",14,true));final EditText box=edit("0",true);box.setText(oldBox);form.addView(box,lp(-1,48));
            form.addView(label("Ream quantity",14,true));final EditText ream=edit("0",true);ream.setText(oldReam);form.addView(ream,lp(-1,48));
            final AlertDialog dialog=new ThemedDialogBuilder().setTitle(tx("Edit Withdrawal")).setView(form).setNegativeButton(tx("Cancel"),null).setPositiveButton(tx("Save changes"),null).create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){applyDialogTheme(dialog);dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));dialog.getButton(-1).setTextColor(TEXT_MAIN);dialog.getButton(-2).setTextColor(TEXT_MAIN);dialog.getButton(-1).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                if(names.size()==0){Toast.makeText(MainActivity.this,tx("Add a product in inventory first"),Toast.LENGTH_SHORT).show();return;}
                try{double newB=qty(box.getText().toString()),newR=qty(ream.getText().toString());String newProduct=product.getText().toString().trim();int newIdx=find(newProduct);
                    if(oldProductIndex<0||newIdx<0){Toast.makeText(MainActivity.this,tx("Could not match the product to inventory"),Toast.LENGTH_LONG).show();return;}
                    // Undo the previous withdrawal before applying the edited values.
                    while(beginningBoxes.size()<=oldProductIndex)beginningBoxes.add(boxes.get(beginningBoxes.size()));while(beginningReams.size()<=oldProductIndex)beginningReams.add(reams.get(beginningReams.size()));
                    beginningBoxes.set(oldProductIndex,Math.max(0,beginningBoxes.get(oldProductIndex)-qty(stockInOldBox)));beginningReams.set(oldProductIndex,Math.max(0,beginningReams.get(oldProductIndex)-qty(stockInOldReam)));boxes.set(oldProductIndex,Math.max(0,boxes.get(oldProductIndex)-qty(stockInOldBox)));reams.set(oldProductIndex,Math.max(0,reams.get(oldProductIndex)-qty(stockInOldReam)));
                    while(beginningBoxes.size()<=newIdx)beginningBoxes.add(boxes.get(beginningBoxes.size()));while(beginningReams.size()<=newIdx)beginningReams.add(reams.get(beginningReams.size()));
                    beginningBoxes.set(newIdx,beginningBoxes.get(newIdx)+newB);beginningReams.set(newIdx,beginningReams.get(newIdx)+newR);boxes.set(newIdx,boxes.get(newIdx)+newB);reams.set(newIdx,reams.get(newIdx)+newR);
                    list.set(index,"Date: "+recordDate[0]+"\n"+newProduct+"\nBox: "+qtyText(newB)+"  |  Ream: "+qtyText(newR));save();dialog.dismiss();showTab(currentTab);
                }catch(Exception ex){Toast.makeText(MainActivity.this,tx("Enter valid non-negative quantities"),Toast.LENGTH_SHORT).show();}
            }});}});dialog.show();
        }
    }
    private void deleteRecord(final String mode,final int index){
        final ArrayList<String> list=mode.equals("ENDING INVENTORY")?endingRecords:withdrawRecords;if(index<0||index>=list.size())return;
        new ThemedDialogBuilder().setMessage(tx("Delete this record?")).setNegativeButton(tx("Cancel"),null).setPositiveButton(tx("Delete"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            if(mode.equals("STOCK IN")){String[] lines=list.get(index).split("\n");String product="",b="0",r="0";for(int i=0;i<lines.length;i++){String line=lines[i].trim();if(line.startsWith("Box:")){String[] parts=line.split("\\|");if(parts.length>0)b=parts[0].replace("Box:","").trim();if(parts.length>1)r=parts[1].replace("Ream:","").trim();}else if(line.length()>0&&!line.startsWith("Date:"))product=line;}int idx=find(product);if(idx>=0){while(beginningBoxes.size()<=idx)beginningBoxes.add(boxes.get(beginningBoxes.size()));while(beginningReams.size()<=idx)beginningReams.add(reams.get(beginningReams.size()));beginningBoxes.set(idx,Math.max(0,beginningBoxes.get(idx)-qty(b)));beginningReams.set(idx,Math.max(0,beginningReams.get(idx)-qty(r)));boxes.set(idx,Math.max(0,boxes.get(idx)-qty(b)));reams.set(idx,Math.max(0,reams.get(idx)-qty(r)));}}
            list.remove(index);save();showTab(currentTab);}}).show();
    }

    private void editProduct(final String old) {
        final int idx=find(old);if(idx<0)return;
        final LinearLayout form=col();form.setPadding(dp(12),dp(8),dp(12),dp(8));
        final EditText name=edit("Product name",false),box=edit("0",true),ream=edit("0",true);
        capitalizeFirstInputLetter(name);
        name.setText(names.get(idx));box.setText(qtyText(boxes.get(idx)));ream.setText(qtyText(reams.get(idx)));
        form.addView(label("Product name",14,true));form.addView(name,lp(-1,48));form.addView(label("Box quantity",14,true));form.addView(box,lp(-1,48));form.addView(label("Ream quantity",14,true));form.addView(ream,lp(-1,48));
        final AlertDialog dialog=new ThemedDialogBuilder().setTitle(tx("Edit Product / Stock")).setView(form).setNegativeButton(tx("Cancel"),null).setNeutralButton(tx("Delete"),null).setPositiveButton(tx("Save"),null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){applyDialogTheme(dialog);dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));dialog.getButton(-2).setTextColor(TEXT_MAIN);dialog.getButton(-3).setTextColor(TEXT_MAIN);dialog.getButton(-1).setTextColor(TEXT_MAIN);
            dialog.getButton(-1).setOnClickListener(new View.OnClickListener(){public void onClick(View v){String n=name.getText().toString().trim();if(n.length()==0||(find(n)>=0&&!n.equalsIgnoreCase(old))){name.setError("Name is empty or already used");return;}try{double b=qty(box.getText().toString()),r=qty(ream.getText().toString());names.set(idx,n);boxes.set(idx,b);reams.set(idx,r);while(productPrices.size()<=idx)productPrices.add(1420.0);if(idx<beginningBoxes.size())beginningBoxes.set(idx,b);if(idx<beginningReams.size())beginningReams.set(idx,r);for(int j=0;j<sales.size();j++)if(sales.get(j).product.equals(old))sales.get(j).product=n;save();dialog.dismiss();showTab(currentTab);}catch(Exception ex){Toast.makeText(MainActivity.this,tx("Enter valid non-negative quantities"),Toast.LENGTH_SHORT).show();}}});
            dialog.getButton(-3).setOnClickListener(new View.OnClickListener(){public void onClick(View v){new ThemedDialogBuilder().setMessage(tx("Delete this product from inventory? Sales history will remain.")).setNegativeButton(tx("Cancel"),null).setPositiveButton(tx("Delete"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface dd,int ww){int j=find(old);if(j>=0){names.remove(j);boxes.remove(j);reams.remove(j);if(j<productPrices.size())productPrices.remove(j);if(j<beginningBoxes.size())beginningBoxes.remove(j);if(j<beginningReams.size())beginningReams.remove(j);}save();dialog.dismiss();showTab(currentTab);}}).show();}});
        }});dialog.show();
    }

    private void buildSalesInput() {
        buildUnifiedSalesInput();
    }
    private void buildUnifiedSalesInput() {
        LinearLayout c=col();
        c.setBackgroundColor(PAGE_BG);
        // Credit mode uses a compact 2dp layout; Cash keeps the existing spacing.
        c.setPadding(dp(14),dp(creditMode ? 2 : 12),dp(14),dp(creditMode ? 2 : 8));
        c.setGravity(Gravity.TOP);
        c.setBaselineAligned(false);

        LinearLayout customerAddressRow=row();
        LinearLayout customerCol=col(); LinearLayout addressCol=col();
        TextView customerLabel=label("CUSTOMER NAME",13,true); customerLabel.setTextColor(TEXT_MUTED);
        TextView addressLabel=label("ADDRESS",13,true); addressLabel.setTextColor(TEXT_MUTED);
        customerCol.addView(customerLabel,lp(-1,-2)); addressCol.addView(addressLabel,lp(-1,-2));
        AutoCompleteTextView customerSuggestions=new AutoCompleteTextView(this);
        customerSuggestions.setSingleLine(true); customerSuggestions.setTextColor(TEXT_MAIN); customerSuggestions.setHintTextColor(TEXT_MUTED);
        customerSuggestions.setHint(tx("Customer name")); customerSuggestions.setTextSize(TypedValue.COMPLEX_UNIT_SP,16);
        customerSuggestions.setPadding(dp(9),0,dp(6),0); customerSuggestions.setBackground(rounded(FIELD_BG,10,BORDER));
        customerSuggestions.setThreshold(1); customerSuggestions.setDropDownBackgroundDrawable(new ColorDrawable(CARD_BG)); capitalizeFirstInputLetter(customerSuggestions);
        ArrayList<String> rememberedCustomers=new ArrayList<String>(); HashSet<String> seenCustomerNames=new HashSet<String>();
        for(Sale oldSale:sales){String remembered=oldSale.customer==null?"":oldSale.customer.trim(); if(remembered.length()==0||remembered.toLowerCase(Locale.US).startsWith("cash-"))continue; String key=remembered.toLowerCase(Locale.US); if(seenCustomerNames.add(key))rememberedCustomers.add(remembered);}
        ArrayAdapter<String> customerAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,rememberedCustomers){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){TextView t=(TextView)super.getView(position,convertView,parent);t.setTextColor(TEXT_MAIN);t.setBackgroundColor(CARD_BG);t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;}
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){TextView t=(TextView)super.getDropDownView(position,convertView,parent);t.setTextColor(TEXT_MAIN);t.setBackgroundColor(CARD_BG);t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;}
        };
        customerSuggestions.setAdapter(customerAdapter); customer=customerSuggestions; customer.setText(prefs.getString("draftCustomer",""));
        customerAddress=new AutoCompleteTextView(this); customerAddress.setSingleLine(true); customerAddress.setTextColor(TEXT_MAIN); customerAddress.setHintTextColor(TEXT_MUTED);
        customerAddress.setHint(tx("Address")); customerAddress.setTextSize(TypedValue.COMPLEX_UNIT_SP,16); customerAddress.setPadding(dp(9),0,dp(6),0);
        customerAddress.setBackground(rounded(FIELD_BG,10,BORDER)); customerAddress.setThreshold(1); customerAddress.setDropDownBackgroundDrawable(new ColorDrawable(CARD_BG));
        ArrayList<String> rememberedAddresses=new ArrayList<String>(); HashSet<String> seenAddresses=new HashSet<String>();
        for(Sale oldSale:sales){String remembered=oldSale.address==null?"":oldSale.address.trim(); String key=remembered.toLowerCase(Locale.US); if(remembered.length()>0&&seenAddresses.add(key))rememberedAddresses.add(remembered);}
        ArrayAdapter<String> addressAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,rememberedAddresses){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){TextView t=(TextView)super.getView(position,convertView,parent);t.setTextColor(TEXT_MAIN);t.setBackgroundColor(CARD_BG);t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;}
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){TextView t=(TextView)super.getDropDownView(position,convertView,parent);t.setTextColor(TEXT_MAIN);t.setBackgroundColor(CARD_BG);t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;}
        };
        customerAddress.setAdapter(addressAdapter); customerAddress.setText(getSalesDraft(creditMode).address);
        customerAddress.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){getSalesDraft(creditMode).address=s.toString();prefs.edit().putString("draftAddress",s.toString()).putString(creditMode ? "creditDraftAddress" : "cashDraftAddress",s.toString()).apply();refreshAdvancedTempOrder();}
            public void afterTextChanged(android.text.Editable e){}
        });
        LinearLayout.LayoutParams half1=new LinearLayout.LayoutParams(0,-2,1f); half1.setMargins(0,0,dp(4),0);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1f); half2.setMargins(dp(4),0,0,0);
        customerCol.addView(customer,lp(-1,46)); addressCol.addView(customerAddress,lp(-1,46)); customerAddressRow.addView(customerCol,half1); customerAddressRow.addView(addressCol,half2);
        LinearLayout.LayoutParams customerRowLp=lp(-1,-2); customerRowLp.setMargins(0,dp(creditMode?2:4),0,dp(creditMode?2:8)); c.addView(customerAddressRow,customerRowLp);

        TextView productLabel=label("SELECT PRODUCT",13,true);
        productLabel.setTextColor(TEXT_MUTED);
        LinearLayout.LayoutParams productLabelLp=lp(-1,-2);
        productLabelLp.topMargin=0;
        c.addView(productLabel,productLabelLp);

        productSpinner=new Spinner(this);
        productSpinner.setBackgroundColor(FIELD_BG);
        productSpinner.setPadding(dp(7),0,dp(7),0);
        if(android.os.Build.VERSION.SDK_INT>=16) productSpinner.setPopupBackgroundDrawable(new ColorDrawable(FIELD_BG));
        LinearLayout.LayoutParams productSpinnerLp=lp(-1,48); productSpinnerLp.setMargins(0,dp(creditMode ? 2 : 4),0,dp(creditMode ? 2 : 8)); c.addView(productSpinner,productSpinnerLp);
        fillSpinner();
        int savedProduct=prefs.getInt("draftProduct",0);
        if(savedProduct>=0 && savedProduct<productSpinner.getCount()) productSpinner.setSelection(savedProduct);

        // Quantity row: no cash input. The sale amount is calculated automatically from the selected product price.
        LinearLayout q=row();
        LinearLayout boxCol=col();
        LinearLayout reamCol=col();
        TextView boxLabel=label("BOX QTY.",13,true); boxLabel.setTextColor(TEXT_MUTED);
        TextView reamLabel=label("REAM QTY.",13,true); reamLabel.setTextColor(TEXT_MUTED);
        boxCol.addView(boxLabel); reamCol.addView(reamLabel);

        saleBox=edit("0",true); saleReam=edit("0",true);
        setSp(saleBox, 18); setSp(saleReam, 18);
        saleBox.setSelectAllOnFocus(true); saleReam.setSelectAllOnFocus(true);
        String savedBoxQty = prefs.getString("draftSaleBox", "");
        String savedReamQty = prefs.getString("draftSaleReam", "");
        // Older drafts may contain a literal zero; show zero only as the hint.
        saleBox.setText(tx("0").equals(savedBoxQty.trim()) ? "" : savedBoxQty);
        saleReam.setText(tx("0").equals(savedReamQty.trim()) ? "" : savedReamQty);
        boxCol.addView(saleBox,lp(-1,50)); reamCol.addView(saleReam,lp(-1,50));

        LinearLayout.LayoutParams boxColLp=new LinearLayout.LayoutParams(0,-2,1); boxColLp.setMargins(0,0,dp(4),0);
        LinearLayout.LayoutParams reamColLp=new LinearLayout.LayoutParams(0,-2,1); reamColLp.setMargins(dp(4),0,0,0);
        q.addView(boxCol,boxColLp); q.addView(reamCol,reamColLp);
        LinearLayout.LayoutParams quantityRowLp=lp(-1,-2); quantityRowLp.setMargins(0,0,0,dp(creditMode ? 2 : 10)); c.addView(q,quantityRowLp);

        partialPayment = null;
        receivableCollection = null;
        if (creditMode) {
            TextView partialLabel=label("PARTIAL",13,true); partialLabel.setTextColor(TEXT_MUTED);
            c.addView(partialLabel,lp(-1,-2));
            partialPayment=edit("0.00",true); setSp(partialPayment,16);
            partialPayment.setText(prefs.getString("draftPartialPayment","0"));
            LinearLayout.LayoutParams partialLp=lp(-1,44); partialLp.setMargins(0,dp(2),0,dp(2)); c.addView(partialPayment,partialLp);
            TextView collectionLabel=label("RECEIVABLE COLLECTION",13,true); collectionLabel.setTextColor(TEXT_MUTED);
            c.addView(collectionLabel,lp(-1,-2));
            receivableCollection=edit("0.00",true); setSp(receivableCollection,16);
            // One-time safety migration: older builds auto-filled the entire debt here,
            // so discard that possibly-unconfirmed draft value instead of recording it as paid.
            if (!prefs.getBoolean("actualReceivableInputV2", false)) {
                prefs.edit().putBoolean("actualReceivableInputV2", true).putString("draftReceivable", "0").apply();
            }
            receivableCollection.setText(prefs.getString("draftReceivable","0"));
            LinearLayout.LayoutParams collectionLp=lp(-1,44); collectionLp.setMargins(0,dp(2),0,dp(6)); c.addView(receivableCollection,collectionLp);
        }
        if (creditMode) {
            android.text.TextWatcher paymentSummaryWatcher = new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
                public void onTextChanged(CharSequence s,int start,int before,int count) { refreshAdvancedTempOrder(); }
                public void afterTextChanged(android.text.Editable e) {}
            };
            partialPayment.addTextChangedListener(paymentSummaryWatcher);
            receivableCollection.addTextChangedListener(paymentSummaryWatcher);

            // Never prefill a payment with the customer's whole debt. That would
            // accidentally mark the debt as paid without the user entering a real amount.
            // The collection field remains zero until the user enters the actual payment.
            customer.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
                public void onTextChanged(CharSequence s,int start,int before,int count) {
                    if (receivableCollection == null) return;
                    if (partialPayment != null) {
                        String partialText = partialPayment.getText().toString().trim();
                        try {
                            if (Double.parseDouble(partialText.isEmpty() ? "0" : partialText) > 0) {
                                partialPayment.setText(tx("0"));
                                partialPayment.setSelection(partialPayment.getText().length());
                            }
                        } catch (Exception ignored) {
                            partialPayment.setText(tx("0"));
                        }
                    }
                    receivableCollection.setText(tx("0"));
                    receivableCollection.setSelection(receivableCollection.getText().length());
                }
                public void afterTextChanged(android.text.Editable e) {}
            });
            receivableCollection.setHint(tx("Actual amount collected"));
        }

        Button add=btn("＋  ADD ORDER");
        styleSalesActionButton(add);
        LinearLayout.LayoutParams addLp=lp(-1,48);
        addLp.setMargins(0,dp(creditMode ? 4 : 0),0,dp(creditMode ? 2 : 10));
        c.addView(add,addLp);
        add.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            // Close the keyboard as soon as ADD ORDER is tapped so SAVE ORDER
            // becomes visible and easy to press in both Cash and Credit modes.
            View focused = getCurrentFocus();
            if (focused != null) {
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                focused.clearFocus();
            }
            addAdvancedTempProduct();
        }});

        LinearLayout orderHeadingRow=row();
        orderHeadingRow.setGravity(Gravity.CENTER_VERTICAL);
        orderHeadingRow.setPadding(dp(2), 0, dp(6), 0);
        TextView orderHeading=label("ORDER SUMMARY",17,true);
        orderHeading.setPadding(0,0,dp(8),0);
        orderHeadingRow.addView(orderHeading,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView clearOrderText=label("CLEAR",15,true);
        clearOrderText.setTextColor(NAVY);
        clearOrderText.setGravity(Gravity.CENTER);
        clearOrderText.setPadding(dp(10),dp(4),dp(4),dp(4));
        clearOrderText.setClickable(true);
        clearOrderText.setFocusable(true);
        clearOrderText.setContentDescription(tx("Clear current order and input fields"));
        orderHeadingRow.addView(clearOrderText,lp(-2,34));
        clearOrderText.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                new ThemedDialogBuilder()
                    .setTitle(tx("Clear current order?"))
                    .setMessage(tx("This will remove all products from the unsaved order and clear the current input fields. Saved sales records will not be affected."))
                    .setNegativeButton(tx("CANCEL"), new DialogInterface.OnClickListener(){ public void onClick(DialogInterface d,int w){ d.dismiss(); }})
                    .setPositiveButton(tx("CLEAR"), new DialogInterface.OnClickListener(){ public void onClick(DialogInterface d,int w){ clearAdvancedOrderDraft(); }})
                    .show();
            }
        });
        LinearLayout.LayoutParams orderHeadingLp=lp(-1,34);
        orderHeadingLp.setMargins(0,dp(creditMode ? 4 : 0),0,dp(creditMode ? 6 : 8));
        c.addView(orderHeadingRow,orderHeadingLp);

        // Keep the order summary in its own bounded scroll area. This lets long
        // orders scroll independently without pushing the action buttons too far down.
        ScrollView orderListScroll=new ScrollView(this);
        // Keep the Order Summary list clean, without an outer border line.
        orderListScroll.setBackgroundColor(PAGE_BG);
        orderListScroll.setPadding(0, 0, 0, 0);
        orderListScroll.setFillViewport(false);
        orderListScroll.setVerticalScrollBarEnabled(true);
        orderListScroll.setNestedScrollingEnabled(true);
        orderListScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        tempOrderList=col();
        orderListScroll.addView(tempOrderList,new ScrollView.LayoutParams(-1,-2));
        // The order summary takes the remaining available height and is the only
        // vertically scrollable region on the Order page.
        LinearLayout.LayoutParams listScrollLp;
        if (creditMode) {
            // Let the outer Credit page scroll the complete order summary as well.
            orderListScroll.setNestedScrollingEnabled(false);
            listScrollLp = lp(-1, -2);
        } else {
            listScrollLp = new LinearLayout.LayoutParams(-1,0,1f);
        }
        listScrollLp.setMargins(0,0,0,dp(creditMode ? 2 : 6));
        c.addView(orderListScroll,listScrollLp);

        refreshAdvancedTempOrder();

        // Keep all order actions in one compact group so the parent cannot
        // distribute spare vertical space between the list and the buttons.
        LinearLayout orderActions=col();
        orderActions.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams orderActionsLp=lp(-1,-2);
        // Keep the action group close to the order summary with consistent spacing.
        // Keep actions anchored below the flexible, independently scrollable list.
        orderActionsLp.setMargins(0,dp(creditMode ? 2 : 3),0,0);
        c.addView(orderActions,orderActionsLp);

        Button saveOrder=btn("✓  SAVE ORDER");
        styleUniformActionButton(saveOrder);
        LinearLayout.LayoutParams saveLp=lp(-1,48);
        saveLp.setMargins(0,0,0,0);
        orderActions.addView(saveOrder,saveLp);
        saveOrder.setOnClickListener(new View.OnClickListener(){public void onClick(View v){enterAdvancedCustomerOrder();}});

        // When the keyboard opens, temporarily hide the bottom action buttons so
        // the Order Summary can use the available screen height. Restore them
        // automatically when the keyboard closes. This avoids the buttons
        // consuming the space needed to see newly added products while typing.
        final LinearLayout actionsForKeyboard = orderActions;
        final View decorView = getWindow().getDecorView();
        c.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                private final Rect visibleFrame = new Rect();
                public void onGlobalLayout() {
                    decorView.getWindowVisibleDisplayFrame(visibleFrame);
                    int screenHeight = decorView.getRootView().getHeight();
                    int obscuredHeight = screenHeight - visibleFrame.bottom;
                    boolean keyboardVisible = obscuredHeight > screenHeight * 0.18;
                    actionsForKeyboard.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);
                }
            });

        c.setClipToPadding(false);
        if (creditMode) {
            // Keep the credit form and order summary scrollable, while anchoring
            // SAVE ORDER at the bottom of the page just like Cash mode.
            compactCreditPage(c);
            c.removeView(orderActions);
            ScrollView creditPageScroll = new ScrollView(this);
            creditPageScroll.setFillViewport(true);
            creditPageScroll.setVerticalScrollBarEnabled(true);
            creditPageScroll.setClipToPadding(false);
            creditPageScroll.addView(c, new ScrollView.LayoutParams(-1, -2));
            host.addView(creditPageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
            orderActions.setPadding(dp(14), dp(2), dp(14), dp(4));
            orderActions.setBackgroundColor(PAGE_BG);
            host.addView(orderActions, new LinearLayout.LayoutParams(-1, -2));
        } else {
            host.addView(c,new LinearLayout.LayoutParams(-1,-1));
        }
    }

    private void compactCreditPage(View view) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            float currentSp = tv.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
            setSp(tv, Math.max(8, Math.round(currentSp - 2)));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) compactCreditPage(group.getChildAt(i));
        }
        android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height > dp(36)) {
            params.height = Math.max(dp(34), params.height - dp(2));
            view.setLayoutParams(params);
        }
    }

    // Automatically uppercase the first character typed in a customer/product name field.
    // Only the first character is changed; the rest of the user's text is preserved.
    private void capitalizeFirstInputLetter(final EditText field) {
        if (field == null) return;
        field.addTextChangedListener(new android.text.TextWatcher() {
            private boolean changing = false;
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable editable) {
                if (changing || editable.length() == 0) return;
                char first = editable.charAt(0);
                char upper = Character.toUpperCase(first);
                if (first != upper) {
                    changing = true;
                    int selection = field.getSelectionStart();
                    editable.replace(0, 1, String.valueOf(upper));
                    if (selection >= 0) field.setSelection(Math.min(selection, editable.length()));
                    changing = false;
                }
            }
        });
    }

    private void clearAdvancedOrderDraft() {
        tempProducts.clear();
        tempBoxes.clear();
        tempReams.clear();
        if (customer != null) customer.setText(tx(""));
        if (customerAddress != null) customerAddress.setText(tx(""));
        if (saleBox != null) saleBox.setText(tx(""));
        if (saleReam != null) saleReam.setText(tx(""));
        if (saleCash != null) saleCash.setText(tx("0"));
        if (partialPayment != null) partialPayment.setText(tx("0"));
        if (receivableCollection != null) receivableCollection.setText(tx("0"));
        if (productSpinner != null && productSpinner.getCount() > 0) productSpinner.setSelection(0);
        prefs.edit()
            .putString("draftCustomer", "")
            .putString("draftAddress", "")
            .putString("draftSaleCash", "0")
            .putString("draftReceivable", "0")
            .putString("draftPartialPayment", "0")
            .putString("draftSaleBox", "")
            .putString("draftSaleReam", "")
            .putInt("draftProduct", 0)
            .apply();
        refreshAdvancedTempOrder();
        Toast.makeText(this,tx("Current order and input fields cleared"), Toast.LENGTH_SHORT).show();
    }

    private void addAdvancedTempProduct() {
        String cname=customer.getText().toString().trim();
        int pos=productSpinner.getSelectedItemPosition();
        if(pos<=0 || pos-1>=names.size()){Toast.makeText(this,tx("Please select a product"),Toast.LENGTH_SHORT).show();return;}
        try {
            double b=qty(saleBox.getText().toString()), r=qty(saleReam.getText().toString());
            if(b==0&&r==0){Toast.makeText(this,tx("Enter a box or ream quantity"),Toast.LENGTH_SHORT).show();return;}
            String product=names.get(pos-1);
            for(int i=0;i<tempProducts.size();i++) if(tempProducts.get(i).equalsIgnoreCase(product)){
                tempBoxes.set(i,tempBoxes.get(i)+b); tempReams.set(i,tempReams.get(i)+r); refreshAdvancedTempOrder(); saleBox.setText(tx("")); saleReam.setText(tx("")); return;
            }
            tempProducts.add(product); tempBoxes.add(b); tempReams.add(r);
            refreshAdvancedTempOrder(); saleBox.setText(tx("")); saleReam.setText(tx(""));
        } catch(Exception ex){Toast.makeText(this,tx("Enter valid non-negative quantities"),Toast.LENGTH_LONG).show();}
    }

    private void refreshAdvancedTempOrder() {
        if(tempOrderList==null)return;
        tempOrderList.removeAllViews();
        if(tempProducts.size()==0){
            if(tempOrderTotalValue!=null) tempOrderTotalValue.setText(moneyText(0));
            LinearLayout summaryCard=col();
            styleCard(summaryCard,CARD_BG);
            summaryCard.setPadding(dp(12),dp(9),dp(12),dp(9));
            TextView empty=label("No products added yet",15,false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8),dp(8),dp(8),dp(8));
            summaryCard.addView(empty,lp(-1,-2));
            if(customerAddress!=null && customerAddress.getText().toString().trim().length()>0) { TextView addressRow=label("ADDRESS: "+customerAddress.getText().toString().trim(),13,false); addressRow.setTextColor(TEXT_MUTED); summaryCard.addView(addressRow,lp(-1,-2)); }
            if (creditMode) {
                double partialAmount = 0, collectionAmount = 0;
                try { if (partialPayment != null) partialAmount = Math.max(0, Double.parseDouble(partialPayment.getText().toString().trim())); } catch (Exception ignored) {}
                try { if (receivableCollection != null) collectionAmount = Math.max(0, Double.parseDouble(receivableCollection.getText().toString().trim())); } catch (Exception ignored) {}
                if (partialAmount > 0) addOrderSummaryMoneyRow(summaryCard, "PARTIAL PAYMENT", partialAmount);
                if (collectionAmount > 0) addOrderSummaryMoneyRow(summaryCard, "RECEIVABLE COLLECTION", collectionAmount);
            }
            tempOrderList.addView(summaryCard,lp(-1,-2));
            save(); return;
        }

        // One combined summary card: list every product and its quantity,
        // then show the total amount once below the complete list.
        LinearLayout summaryCard=col();
        styleCard(summaryCard,CARD_BG);
        summaryCard.setPadding(dp(12),dp(9),dp(12),dp(9));
        double totalAmount=0;
        for(int i=0;i<tempProducts.size();i++){
            final int index=i;
            totalAmount+=saleAmount(tempProducts.get(i),tempBoxes.get(i),tempReams.get(i));
            LinearLayout productLine=row();
            productLine.setGravity(Gravity.CENTER_VERTICAL);
            TextView productName=label(tempProducts.get(i),14,true);
            productName.setTextColor(TEXT_MAIN);
            TextView quantity=label(qtyText(tempBoxes.get(i))+" box  •  "+qtyText(tempReams.get(i))+" ream",12,true);
            quantity.setTextColor(TEXT_MAIN);
            quantity.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            productLine.addView(productName,new LinearLayout.LayoutParams(0,-2,1));
            productLine.addView(quantity,new LinearLayout.LayoutParams(-2,-2));
            LinearLayout.LayoutParams productLineLp=lp(-1,-2);
            productLineLp.setMargins(0,dp(4),0,dp(4));
            summaryCard.addView(productLine,productLineLp);
            productLine.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                new ThemedDialogBuilder().setTitle(tempProducts.get(index)).setItems(new String[]{"Edit order item","Delete order item"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                    if(which==0){
                        String selected=tempProducts.get(index); double b=tempBoxes.get(index), r=tempReams.get(index);
                        int spinnerPos=names.indexOf(selected); if(spinnerPos>=0 && productSpinner!=null)productSpinner.setSelection(spinnerPos+1);
                        if(saleBox!=null)saleBox.setText(qtyText(b)); if(saleReam!=null)saleReam.setText(qtyText(r));
                        tempProducts.remove(index); tempBoxes.remove(index); tempReams.remove(index); refreshAdvancedTempOrder();
                        Toast.makeText(MainActivity.this,tx("Edit the quantities, then tap ADD PRODUCT"),Toast.LENGTH_LONG).show();
                    }else{
                        new ThemedDialogBuilder().setTitle(tx("Delete order item?")).setMessage("Remove "+tempProducts.get(index)+" from this order?").setNegativeButton(tx("Cancel"),null).setPositiveButton(tx("Delete"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d2,int w2){tempProducts.remove(index);tempBoxes.remove(index);tempReams.remove(index);refreshAdvancedTempOrder();}}).show();
                    }
                }}).setNegativeButton(tx("Cancel"),null).show();
            }});
        }

        if(tempOrderTotalValue!=null) tempOrderTotalValue.setText(moneyText(totalAmount));

        // Show the running total inside the scrollable unsaved-order summary,
        // directly underneath the product rows.
        View totalDivider = new View(this);
        totalDivider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams totalDividerLp = lp(-1,1);
        totalDividerLp.setMargins(0,dp(6),0,dp(5));
        summaryCard.addView(totalDivider,totalDividerLp);
        LinearLayout totalLine = row();
        totalLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView totalLabel = label("TOTAL",14,true);
        totalLabel.setTextColor(TEXT_MAIN);
        TextView totalValue = label(moneyText(totalAmount),16,true);
        totalValue.setTextColor(NAVY);
        totalValue.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        totalLine.addView(totalLabel,new LinearLayout.LayoutParams(0,-2,1));
        totalLine.addView(totalValue,lp(-2,-2));
        summaryCard.addView(totalLine,lp(-1,-2));

        if(customerAddress!=null && customerAddress.getText().toString().trim().length()>0) { TextView addressRow=label("ADDRESS: "+customerAddress.getText().toString().trim(),13,false); addressRow.setTextColor(TEXT_MUTED); summaryCard.addView(addressRow,lp(-1,-2)); }
        if (creditMode) {
            double partialAmount = 0, collectionAmount = 0;
            try { if (partialPayment != null) partialAmount = Math.max(0, Double.parseDouble(partialPayment.getText().toString().trim())); } catch (Exception ignored) {}
            try { if (receivableCollection != null) collectionAmount = Math.max(0, Double.parseDouble(receivableCollection.getText().toString().trim())); } catch (Exception ignored) {}
            if (partialAmount > 0) addOrderSummaryMoneyRow(summaryCard, "PARTIAL PAYMENT", partialAmount);
            if (collectionAmount > 0) addOrderSummaryMoneyRow(summaryCard, "RECEIVABLE COLLECTION", collectionAmount);
        }

        LinearLayout.LayoutParams cardLp=lp(-1,-2);
        cardLp.setMargins(0,dp(2),0,dp(2));
        tempOrderList.addView(summaryCard,cardLp);
        save();
    }



    private void addOrderSummaryMoneyRow(LinearLayout card, String title, double amount) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(title, 12, true);
        name.setTextColor(TEXT_MUTED);
        TextView value = label(moneyText(amount), 14, true);
        styleAmountValue(value);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(value, lp(-2, -2));
        LinearLayout.LayoutParams lineLp = lp(-1, -2);
        lineLp.setMargins(0, dp(3), 0, 0);
        card.addView(line, lineLp);
    }

    // Outstanding balance is the sum of credit balances minus actual collections.
    // Receivable Collection must only be subtracted when a real payment was entered.
    private double getCustomerOutstandingBalance(String customerName, String customerAddressValue) {
        if (customerName == null || customerName.trim().length() == 0) return 0;
        String target = customerName.trim();
        String targetAddress = customerAddressValue == null ? "" : customerAddressValue.trim();
        double outstanding = 0;
        for (Sale sale : sales) {
            if (sale == null || sale.customer == null ||
                    !sale.customer.trim().equalsIgnoreCase(target)) continue;
            String recordedAddress = sale.address == null ? "" : sale.address.trim();
            if (targetAddress.length() > 0 && recordedAddress.length() > 0 && !recordedAddress.equalsIgnoreCase(targetAddress)) continue;
            String productName = sale.product == null ? "" : sale.product.trim();
            if (sale.credit && !productName.equalsIgnoreCase("Receivable Collection")
                    && !productName.startsWith("CHANGE_STOCK::")) {
                outstanding += Math.max(0, sale.balance);
            }
            outstanding -= Math.max(0, sale.receivable);
        }
        return Math.max(0, outstanding);
    }

    private String nextAnonymousCustomerName() {
        int maxNumber=0;
        for(Sale sale:sales){
            String name=sale.customer==null?"":sale.customer.trim();
            if(name.matches("(?i)^Cash-\\d+$")){
                try{maxNumber=Math.max(maxNumber,Integer.parseInt(name.substring(name.indexOf('-')+1)));}catch(Exception ignored){}
            }
        }
        return String.format(Locale.US,"Cash-%02d",maxNumber+1);
    }

    private void enterAdvancedCustomerOrder() {
        String cname=customer.getText().toString().trim();
        double partial = 0, collection = 0;
        try { if (creditMode && partialPayment!=null) partial=Math.max(0,Double.parseDouble(partialPayment.getText().toString().trim())); } catch(Exception ignored) {}
        try { if (creditMode && receivableCollection!=null) collection=Math.max(0,Double.parseDouble(receivableCollection.getText().toString().trim())); } catch(Exception ignored) {}
        // Credit transactions and collections must be attached to a named customer.
        if (creditMode && cname.length() == 0) {
            Toast.makeText(this,tx("Enter the customer's name for a credit order or collection"),Toast.LENGTH_LONG).show();
            return;
        }
        // Allow collecting debt that existed before Sales Point was introduced.
        // If Sales Point already knows this customer's balance, keep the usual
        // over-collection check. If no balance is recorded yet, do not block
        // the agent from recording a payment for a real-world previous debt.
        double previousOutstanding = creditMode ? getCustomerOutstandingBalance(cname, customerAddress==null?"":customerAddress.getText().toString()) : 0;
        if (creditMode && collection > 0 && previousOutstanding > 0.009
                && collection - previousOutstanding > 0.009) {
            Toast.makeText(this,tx("Collection cannot exceed the customer's outstanding balance of ") + moneyText(previousOutstanding),Toast.LENGTH_LONG).show();
            return;
        }
        // Credit customers may pay an old receivable without buying new products.
        if(tempProducts.size()==0 && (!creditMode || collection<=0)){
            Toast.makeText(this,tx("Add a product or enter an actual Receivable Collection payment first"),Toast.LENGTH_LONG).show();return;
        }
        if(cname.length()==0)cname=nextAnonymousCustomerName();
        if(tempProducts.size()==0 && creditMode && collection>0){
            Sale collectionSale = new Sale(today(), cname, "Receivable Collection", 0, 0, 0, collection, 1.0);
            collectionSale.address=customerAddress==null?"":customerAddress.getText().toString().trim();
            collectionSale.credit = true;
            collectionSale.partial = 0;
            collectionSale.balance = 0;
            sales.add(0, collectionSale);
            tempProducts.clear(); tempBoxes.clear(); tempReams.clear();
            customer.setText(tx(""));
            if(customerAddress!=null)customerAddress.setText(tx(""));
            if(saleCash!=null) saleCash.setText(tx("0"));
            if(saleBox!=null) saleBox.setText(tx(""));
            if(saleReam!=null) saleReam.setText(tx(""));
            if(partialPayment!=null) partialPayment.setText(tx("0"));
            if(receivableCollection!=null) receivableCollection.setText(tx("0"));
            if(productSpinner!=null && productSpinner.getCount()>0) productSpinner.setSelection(0);
            prefs.edit().putString("draftCustomer","").putString("draftAddress","").putString("draftSaleCash","").putString("draftReceivable","").putString("draftPartialPayment","0").putString("draftSaleBox","").putString("draftSaleReam","").putInt("draftProduct",0).apply();
            save();
            Toast.makeText(this,tx("Receivable collection saved"),Toast.LENGTH_SHORT).show();
            showTab(1);
            return;
        }
        double orderTotal=0; for(int ti=0;ti<tempProducts.size();ti++) orderTotal += tempBoxes.get(ti)*(reamPrice(tempProducts.get(ti))*50.0)+tempReams.get(ti)*reamPrice(tempProducts.get(ti));
        if (creditMode && partial - orderTotal > 0.009) {
            Toast.makeText(this,tx("Partial payment cannot exceed the new order amount of ") + moneyText(orderTotal),Toast.LENGTH_LONG).show();
            return;
        }
        // Validate the complete order before changing stock.
        for(int i=0;i<tempProducts.size();i++){
            int idx=find(tempProducts.get(i)); if(idx<0)return;
            double neededBox=tempBoxes.get(i), neededReam=tempReams.get(i);
            double convert=neededReam>reams.get(idx)?Math.ceil((neededReam-reams.get(idx))/50.0):0;
            if(neededBox+convert>boxes.get(idx) || neededReam>reams.get(idx)+convert*50.0){
                Toast.makeText(this,tx("Not enough stock for ")+tempProducts.get(i),Toast.LENGTH_LONG).show(); return;
            }
        }
        String date=today();
        for(int i=0;i<tempProducts.size();i++){
            int idx=find(tempProducts.get(i)); double b=tempBoxes.get(i),r=tempReams.get(i);
            if(r>reams.get(idx)){double convert=Math.ceil((r-reams.get(idx))/50.0);boxes.set(idx,boxes.get(idx)-convert);reams.set(idx,reams.get(idx)+convert*50.0);}
            boxes.set(idx,boxes.get(idx)-b); reams.set(idx,reams.get(idx)-r);
            // Payment/collection belongs to the customer order once; keep it on the first product line only.
            // Record payment entries on this order only; do not search for or modify older customer records.
            Sale savedSale=new Sale(date,cname,tempProducts.get(i),b,r,creditMode?partial:0,
                    (creditMode && i==0)?collection:0,reamPrice(tempProducts.get(i)));
            savedSale.address=customerAddress==null?"":customerAddress.getText().toString().trim();
            savedSale.credit=creditMode; savedSale.partial=(creditMode && i==0)?partial:0; savedSale.balance=(creditMode && i==0)?Math.max(0,orderTotal-partial):0; if(!creditMode) savedSale.cash=savedSale.amount();
            sales.add(0,savedSale);
        }
        tempProducts.clear();tempBoxes.clear();tempReams.clear();
        customer.setText(tx("")); if(customerAddress!=null)customerAddress.setText(tx("")); if(saleCash!=null) saleCash.setText(tx("0")); saleBox.setText(tx("")); saleReam.setText(tx(""));
        if(productSpinner!=null && productSpinner.getCount()>0) productSpinner.setSelection(0);
        prefs.edit().putString("draftCustomer","").putString("draftAddress","").putString("draftSaleCash","").putString("draftReceivable","").putString("draftPartialPayment","").putString("draftSaleBox","").putString("draftSaleReam","").putInt("draftProduct",0).apply();
        save(); Toast.makeText(this,tx("Customer order saved"),Toast.LENGTH_SHORT).show(); showTab(1);
    }




    // Theme-aware spinner adapter: explicitly styles both the selected value and every dropdown row.
    // Android's stock spinner layouts can retain dark text in light mode (or vice versa).
    private ArrayAdapter<String> themedSpinnerAdapter(final List<String> items) {
        return themedSpinnerAdapter(items, 16);
    }

    private ArrayAdapter<String> themedSpinnerAdapter(final List<String> items, final int textSizeSp) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            private TextView styleSpinnerText(View view, int backgroundColor) {
                TextView text;
                if (view instanceof TextView) {
                    text = (TextView) view;
                } else {
                    text = new TextView(MainActivity.this);
                }
                // Explicitly set both colors on every bind. Android's recycled Spinner
                // views can otherwise keep a stale system-theme background/text color.
                text.setTextColor(TEXT_MAIN);
                text.setBackground(new ColorDrawable(backgroundColor));
                setSp(text, textSizeSp);
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setPadding(dp(12), dp(8), dp(12), dp(8));
                text.setMinHeight(dp(48));
                return text;
            }
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView text = styleSpinnerText(super.getView(position, convertView, parent), FIELD_BG);
                text.setSingleLine(true);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                return text;
            }
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                return styleSpinnerText(super.getDropDownView(position, convertView, parent), FIELD_BG);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void fillSpinner() {
        final ArrayList<String> list=new ArrayList<String>();
        list.add("— Select product —");
        if(names.size()==0) list.add("Add a product in Inventory tab");
        else list.addAll(names);
        // Use the same simple product dropdown style as the Edit Stock In editor.
        ArrayAdapter<String> a=themedSpinnerAdapter(list, 16);
        productSpinner.setAdapter(a);
        productSpinner.setBackgroundColor(FIELD_BG);
        productSpinner.setPadding(dp(8),0,dp(8),0);
        if(android.os.Build.VERSION.SDK_INT>=16) productSpinner.setPopupBackgroundDrawable(new ColorDrawable(FIELD_BG));
    }
    // Weekly and monthly reports live on the Daily Sales page, not Sales History.
    private void buildSalesList() {
        // Every time the Daily Sales page opens, default to today's date so
        // the current weekday button is highlighted automatically.
        salesListSelectedDate = Calendar.getInstance();
        LinearLayout c=col();
        c.setBackgroundColor(PAGE_BG);
        c.setPadding(dp(14),dp(12),dp(14),dp(12));

        // Keep the title and instructions fixed; only the records area scrolls.
        TextView salesListHeading=label("DISPOSAL",18,true);
        salesListHeading.setPadding(dp(3),dp(2),dp(3),0);
        c.addView(salesListHeading);

        // Seven weekday selectors scale with available width. In landscape, fit all
        // seven buttons across the page when possible; in portrait keep the familiar
        // wider horizontally-scrollable buttons.
        HorizontalScrollView dayScroll = new HorizontalScrollView(this);
        salesHistoryDayScroll = dayScroll;
        dayScroll.setHorizontalScrollBarEnabled(false);
        dayScroll.setFillViewport(false);
        LinearLayout dayRow = row();
        dayRow.setGravity(Gravity.CENTER_VERTICAL);
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int dayButtonWidth = 88;
        int dayButtonHeight = 42;
        int dayButtonTextSp = 12;
        if (landscape || screenWidthDp >= 600f) {
            // Account for page side padding (28dp) and each button's 6dp right gap.
            dayButtonWidth = Math.max(56, Math.min(112,
                    (int) ((screenWidthDp - 28f - 42f) / 7f)));
            dayButtonHeight = 38;
            dayButtonTextSp = dayButtonWidth < 76 ? 10 : 11;
        }
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (int i = 0; i < dayNames.length; i++) {
            final int dayIndex = i;
            Button dayButton = btn(dayNames[i]);
            dayButton.setAllCaps(false);
            setSp(dayButton, dayButtonTextSp);
            dayButton.setSingleLine(true);
            dayButton.setMinWidth(0);
            dayButton.setMinimumWidth(0);
            dayButton.setPadding(dp(2), 0, dp(2), 0);
            LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(dp(dayButtonWidth), dp(dayButtonHeight));
            dayLp.setMargins(0, dp(4), dp(6), dp(6));
            dayRow.addView(dayButton, dayLp);
            salesHistoryDayButtons[i] = dayButton;
            dayButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Calendar monday = (Calendar) salesListSelectedDate.clone();
                    monday.set(Calendar.HOUR_OF_DAY, 0); monday.set(Calendar.MINUTE, 0);
                    monday.set(Calendar.SECOND, 0); monday.set(Calendar.MILLISECOND, 0);
                    int dow = monday.get(Calendar.DAY_OF_WEEK);
                    int offset = (dow == Calendar.SUNDAY) ? -6 : Calendar.MONDAY - dow;
                    monday.add(Calendar.DAY_OF_MONTH, offset + dayIndex);
                    salesListSelectedDate.setTimeInMillis(monday.getTimeInMillis());
                    refreshSales();
                }
            });
        }
        dayScroll.addView(dayRow);
        // When Sales List opens, automatically scroll the weekday strip so
        // today's button is visible without the user swiping manually.
        final int todayIndex = salesListSelectedDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 6 : salesListSelectedDate.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
        dayScroll.post(new Runnable() {
            public void run() {
                if (todayIndex >= 0 && todayIndex < salesHistoryDayButtons.length
                        && salesHistoryDayButtons[todayIndex] != null) {
                    Button todayButton = salesHistoryDayButtons[todayIndex];
                    int targetX = todayButton.getLeft() - dp(8);
                    dayScroll.smoothScrollTo(Math.max(0, targetX), 0);
                }
            }
        });
        LinearLayout.LayoutParams dayScrollLp = lp(-1, -2);
        dayScrollLp.setMargins(0, dp(6), 0, dp(4));
        c.addView(dayScroll, dayScrollLp);

        salesList=col();
        ScrollView recordsScroll=new ScrollView(this);
        recordsScroll.setFillViewport(false);
        recordsScroll.setVerticalScrollBarEnabled(true);
        recordsScroll.addView(salesList);
        c.addView(recordsScroll,new LinearLayout.LayoutParams(-1,0,1));

        // Fixed daily totals: these stay visible and do not scroll with customer records.
        salesListSummary = col();
        salesListSummary.setBackground(rounded(CARD_BG, 10, BORDER));
        salesListSummary.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams salesSummaryLp = lp(-1, -2);
        salesSummaryLp.setMargins(0, dp(4), 0, dp(3));
        c.addView(salesListSummary, salesSummaryLp);

        // Weekly and Monthly Sales shortcuts are placed on the Sales List page,
        // immediately above the TOTAL SALES button.
        LinearLayout reportButtons = row();
        reportButtons.setGravity(Gravity.CENTER);
        reportButtons.setPadding(0, 0, 0, 0);
        Button weeklyReportButton = btn("▣  Weekly Sales");
        Button monthlyReportButton = btn("▦  Monthly Sales");
        styleUniformActionButton(weeklyReportButton);
        styleUniformActionButton(monthlyReportButton);
        LinearLayout.LayoutParams weeklyButtonLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        weeklyButtonLp.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams monthlyButtonLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        monthlyButtonLp.setMargins(dp(6), 0, 0, 0);
        reportButtons.addView(weeklyReportButton, weeklyButtonLp);
        reportButtons.addView(monthlyReportButton, monthlyButtonLp);
        LinearLayout.LayoutParams reportButtonsLp = lp(-1, 56);
        reportButtonsLp.setMargins(0, dp(1), 0, 0);
        c.addView(reportButtons, reportButtonsLp);
        weeklyReportButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { buildSalesPeriodPage(true); }
        });
        monthlyReportButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { buildSalesPeriodPage(false); }
        });

        Button viewTotal = btn("▤  ACCOUNTS RECEIVABLE");
        styleUniformActionButton(viewTotal);
        LinearLayout.LayoutParams viewTotalLp = lp(-1, 48);
        viewTotalLp.setMargins(0, dp(2), 0, dp(2));
        c.addView(viewTotal, viewTotalLp);
        viewTotal.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            Calendar now=Calendar.getInstance(); int dow=now.get(Calendar.DAY_OF_WEEK);
            collectionWeekday=(dow==Calendar.SUNDAY)?6:dow-Calendar.MONDAY;
            if(collectionWeekday<0||collectionWeekday>6)collectionWeekday=0;
            collectionCategory=0; buildCollectionPage();
        }});

        host.addView(c,lp(-1,-1));
        refreshSales();
    }

    // Convert the Collection Page weekday index (Monday=0 through Sunday=6)
    // into its visible day name.
    private String dayName(int day) {
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        if (day < 0 || day >= days.length) return "Monday";
        return days[day];
    }

    private void buildCollectionPage() {
        LinearLayout page=col();
        page.setGravity(Gravity.TOP);
        page.setBackground(rounded(CARD_BG,12,BORDER));
        page.setPadding(dp(12),dp(10),dp(12),dp(10));

        TextView heading=label("ACCOUNTS RECEIVABLE",18,true);
        heading.setTextColor(TEXT_MAIN);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(4),0,dp(4),dp(6));
        page.addView(heading,lp(-1,34));

        // Seven days in a horizontal scroller, showing about 3.5 buttons at once.
        HorizontalScrollView dayScroll=new HorizontalScrollView(this);
        dayScroll.setHorizontalScrollBarEnabled(false);
        dayScroll.setFillViewport(false);
        dayScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout weekdayRow=row();
        weekdayRow.setGravity(Gravity.CENTER_VERTICAL);
        Button[] weekdays=new Button[7];
        final String[] dayNames={"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
        float screenWidthDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
        int buttonWidth=Math.max(dp(76),(int)((screenWidthDp-dp(24))/3.5f));
        for(int i=0;i<dayNames.length;i++){
            final int index=i;
            Button day=btn(dayNames[i]);
            day.setAllCaps(false); setSp(day,12);
            day.setMinWidth(0); day.setMinimumWidth(0);
            day.setPadding(dp(3),0,dp(3),0);
            LinearLayout.LayoutParams dayLp=new LinearLayout.LayoutParams(buttonWidth,dp(42));
            dayLp.setMargins(i==0?0:dp(5),0,dp(1),0);
            weekdayRow.addView(day,dayLp); weekdays[i]=day;
            day.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                collectionWeekday=index;
                collectionSelectedDate=collectionDateForWeekday();
                refreshCollectionPage();
            }});
        }
        dayScroll.addView(weekdayRow,new HorizontalScrollView.LayoutParams(-2,-2));
        // Keep the date label close to the weekday buttons.
        LinearLayout.LayoutParams weekdayLp=lp(-1,48);
        weekdayLp.setMargins(0,0,0,0);
        page.addView(dayScroll,weekdayLp);

        final TextView fixedDate=label("",15,true);
        fixedDate.setTextColor(TEXT_MAIN);
        fixedDate.setIncludeFontPadding(false);
        fixedDate.setPadding(dp(4),dp(2),dp(4),dp(4));
        fixedDate.setTag("collection-date");
        fixedDate.setText(tx("▦  Select date"));
        fixedDate.setClickable(true);
        fixedDate.setFocusable(true);
        fixedDate.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                Calendar initial=collectionSelectedDate!=null ? (Calendar)collectionSelectedDate.clone() : collectionDateForWeekday();
                DatePickerDialog picker=new DatePickerDialog(MainActivity.this,
                    (lightTheme ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert),
                    new DatePickerDialog.OnDateSetListener(){
                        @Override public void onDateSet(DatePicker view,int year,int month,int dayOfMonth){
                            Calendar selectedDate=Calendar.getInstance();
                            selectedDate.set(Calendar.YEAR,year);
                            selectedDate.set(Calendar.MONTH,month);
                            selectedDate.set(Calendar.DAY_OF_MONTH,dayOfMonth);
                            selectedDate.set(Calendar.HOUR_OF_DAY,0);
                            selectedDate.set(Calendar.MINUTE,0);
                            selectedDate.set(Calendar.SECOND,0);
                            selectedDate.set(Calendar.MILLISECOND,0);
                            collectionSelectedDate=selectedDate;
                            int dow=selectedDate.get(Calendar.DAY_OF_WEEK);
                            collectionWeekday=(dow==Calendar.SUNDAY)?6:dow-Calendar.MONDAY;
                            refreshCollectionPage();
                        }
                    },initial.get(Calendar.YEAR),initial.get(Calendar.MONTH),initial.get(Calendar.DAY_OF_MONTH));
                picker.getDatePicker().setMaxDate(System.currentTimeMillis());
                showThemedDatePicker(picker);
            }
        });
        page.addView(fixedDate,lp(-1,-2));

        final LinearLayout records=col();
        records.setPadding(0,dp(2),0,dp(2));
        // Wrap the records area when there are only a few entries, but cap it so
        // long lists scroll instead of stretching the whole dialog.
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(records,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams scrollParams=new LinearLayout.LayoutParams(-1,0,1f);
        // Only the records list is flexible; keep it a few dp below the date heading.
        scrollParams.setMargins(0,dp(4),0,dp(2)); page.addView(scroll,scrollParams);

        final LinearLayout totalPanel=col();
        styleCard(totalPanel,FIELD_BG); totalPanel.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams totalLp=lp(-1,-2); totalLp.setMargins(0,dp(4),0,0); page.addView(totalPanel,totalLp);

        final Button closeInPanel=btn("CLOSE");
        closeInPanel.setTextColor(Color.WHITE); closeInPanel.setBackground(rounded(NAVY_DARK,12,NAVY_DARK));
        closeInPanel.setPadding(dp(18),dp(8),dp(18),dp(8));
        LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(-2,dp(42));
        closeParams.gravity=Gravity.RIGHT; closeParams.setMargins(0,dp(8),0,0); page.addView(closeInPanel,closeParams);

        collectionDialogRoot=page;
        collectionDialog=new ThemedDialogBuilder().setView(page).create();
        closeInPanel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){collectionDialog.dismiss();}});
        collectionDialog.show(); applyDialogTheme(collectionDialog);
        if(collectionDialog.getWindow()!=null){
            int screenWidth=getResources().getDisplayMetrics().widthPixels;
            // Match Weekly Sales: fixed compact report dialog, records scroll, totals and CLOSE fixed.
            int availableHeight=getResources().getDisplayMetrics().heightPixels-dp(150);
            int dialogHeight=Math.min(availableHeight,dp(600));
            collectionDialog.getWindow().setLayout(
                Math.min(screenWidth-dp(24),dp(520)),dialogHeight);
        }
        page.setTag(new Object[]{records,totalPanel,weekdays,dayScroll});
        if(collectionSelectedDate==null) collectionSelectedDate=collectionDateForWeekday();
        refreshCollectionPage();
        // Keep the selected day visible when opening the page.
        final int selected=collectionWeekday;
        dayScroll.post(new Runnable(){public void run(){
            if(selected>=0 && selected<weekdays.length) dayScroll.smoothScrollTo(Math.max(0,weekdays[selected].getLeft()-dp(8)),0);
        }});
    }

    private Calendar collectionDateForWeekday() {
        Calendar date=Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY,0); date.set(Calendar.MINUTE,0); date.set(Calendar.SECOND,0); date.set(Calendar.MILLISECOND,0);
        int dow=date.get(Calendar.DAY_OF_WEEK);
        int mondayOffset=(dow==Calendar.SUNDAY)?-6:Calendar.MONDAY-dow;
        date.add(Calendar.DAY_OF_MONTH,mondayOffset+collectionWeekday);
        return date;
    }

    private void refreshCollectionPage() {
        if(collectionDialogRoot==null || !(collectionDialogRoot instanceof LinearLayout))return;
        Object tag=collectionDialogRoot.getTag(); if(!(tag instanceof Object[]))return;
        Object[] refs=(Object[])tag;
        LinearLayout records=(LinearLayout)refs[0], totalPanel=(LinearLayout)refs[1];
        Button[] weekdays=(Button[])refs[2];
        Calendar chosen=collectionSelectedDate!=null ? (Calendar)collectionSelectedDate.clone() : collectionDateForWeekday();
        int selectedDow=chosen.get(Calendar.DAY_OF_WEEK);
        collectionWeekday=(selectedDow==Calendar.SUNDAY)?6:selectedDow-Calendar.MONDAY;
        for(int i=0;i<weekdays.length;i++){
            boolean selected=i==collectionWeekday;
            weekdays[i].setBackground(rounded(selected?NAVY_DARK:HEADER_BG,10,selected?NAVY:BORDER));
            weekdays[i].setTextColor(selected?Color.WHITE:TEXT_MAIN);
        }
        records.removeAllViews(); totalPanel.removeAllViews();
        String chosenDate=new SimpleDateFormat("dd-MMM-yyyy",Locale.US).format(chosen.getTime());
        String chosenDay=dayName(collectionWeekday);
        LinearLayout dateRoot=(LinearLayout)collectionDialogRoot;
        for(int j=0;j<dateRoot.getChildCount();j++){
            View child=dateRoot.getChildAt(j);
            if(child instanceof TextView && "collection-date".equals(child.getTag())){
                ((TextView)child).setText(tx("▦  ")+chosenDay+"  •  "+chosenDate+"  ▾"); break;
            }
        }

        // Group the selected day's activity by customer so each card can show
        // the correct combination of previous receivables, new credit, and partial payment.
        ArrayList<String> customerKeys=new ArrayList<String>();
        ArrayList<ArrayList<Integer>> groups=new ArrayList<ArrayList<Integer>>();
        // IMPORTANT: transactions are inserted with sales.add(0, ...), so index 0 is newest.
        // Iterate forward to ensure the customer with the latest transaction is shown first.
        for(int i=0;i<sales.size();i++){
            Sale sale=sales.get(i);
            if(!sameRecordDate(sale.date,chosenDate))continue;
            String product=sale.product==null?"":sale.product;
            boolean relevant=sale.receivable>0 || sale.partial>0 ||
                    (sale.credit && !product.equalsIgnoreCase("Receivable Collection") && !product.startsWith("CHANGE_STOCK::"));
            if(!relevant)continue;
            String key=(sale.customer==null?"":sale.customer.trim().toLowerCase(Locale.ROOT))+"\u0001"+(sale.address==null?"":sale.address.trim().toLowerCase(Locale.ROOT));
            int g=customerKeys.indexOf(key);
            if(g<0){customerKeys.add(key);groups.add(new ArrayList<Integer>());g=groups.size()-1;}
            groups.get(g).add(i);
        }

        double dayCollection=0,dayNewCreditBalance=0,dayPartialPayment=0;
        for(int g=0;g<groups.size();g++){
            ArrayList<Integer> indexes=groups.get(g);
            Sale firstSale=sales.get(indexes.get(0));
            String customerName=firstSale.customer;
            double collected=0,partial=0,newAmount=0,newBalance=0,newBox=0,newReam=0,oldBox=0,oldReam=0;
            boolean hasNewCredit=false;
            for(int k=0;k<indexes.size();k++){
                Sale sale=sales.get(indexes.get(k));
                String product=sale.product==null?"":sale.product;
                collected+=Math.max(0,sale.receivable);
                partial+=Math.max(0,sale.partial);
                if(sale.credit && !product.equalsIgnoreCase("Receivable Collection") && !product.startsWith("CHANGE_STOCK::")){
                    hasNewCredit=true;
                    newAmount+=sale.amount();
                    newBalance+=Math.max(0,sale.balance);
                    newBox+=sale.box;
                    newReam+=sale.ream;
                }
            }

            // Previous-order quantity is shown when an existing receivable is collected.
            // Count earlier credit orders only; do not include today's new credit order.
            if(collected>0 && customerName!=null){
                String currentDate=firstSale.date;
                for(int k=0;k<sales.size();k++){
                    Sale prior=sales.get(k);
                    if(prior.customer==null || !prior.customer.trim().equalsIgnoreCase(customerName.trim()))continue;
                    String currentAddress=firstSale.address==null?"":firstSale.address.trim();
                    String priorAddress=prior.address==null?"":prior.address.trim();
                    if(currentAddress.length()>0 && priorAddress.length()>0 && !currentAddress.equalsIgnoreCase(priorAddress))continue;
                    String product=prior.product==null?"":prior.product;
                    if(!prior.credit || product.equalsIgnoreCase("Receivable Collection") || product.startsWith("CHANGE_STOCK::"))continue;
                    if(prior.date!=null && !prior.date.equals(currentDate)){
                        oldBox+=prior.box;
                        oldReam+=prior.ream;
                    }
                }
            }

            // For this display, balance is the new-credit amount less today's partial payment.
            double displayedBalance=Math.max(0,newAmount-partial);
            dayCollection+=collected;
            dayNewCreditBalance+=displayedBalance;
            dayPartialPayment+=partial;

            LinearLayout card=col();
            styleCard(card,g%2==0?CARD_BG:HEADER_BG);
            card.setPadding(dp(10),dp(8),dp(10),dp(8));

            LinearLayout customerHeader=row();
            customerHeader.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=label(customerName==null?"":customerName,15,true);
            name.setTextColor(NAVY);
            customerHeader.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
            if(hasNewCredit){
                TextView creditTag=label("CREDIT",11,true);
                // Restore the visible teal highlight for CREDIT on the Collection page.
                creditTag.setTextColor(Color.WHITE);
                creditTag.setGravity(Gravity.CENTER);
                creditTag.setPadding(dp(9),dp(4),dp(9),dp(4));
                creditTag.setBackground(rounded(NAVY_DARK,8,NAVY));
                customerHeader.addView(creditTag,lp(-2,-2));
            }
            card.addView(customerHeader,lp(-1,-2));
            if(firstSale.address!=null && firstSale.address.trim().length()>0){
                TextView addressLine=label(firstSale.address.trim(),13,false);
                addressLine.setTextColor(TEXT_MUTED);
                card.addView(addressLine,lp(-1,-2));
            }

            View sep=new View(this);
            sep.setBackgroundColor(BORDER);
            LinearLayout.LayoutParams sepLp=lp(-1,1);
            sepLp.setMargins(0,dp(6),0,dp(5));
            card.addView(sep,sepLp);

            if(!hasNewCredit && collected>0){
                // Receivables-only payment, with no new order.
                addCollectionDetailRow(card,"ORDER QUANTITY",
                        "Box "+qtyText(oldBox)+"  |  Reams "+qtyText(oldReam));
                addCollectionDetailRow(card,"RECEIVABLE COLLECTION",moneyText(collected));
            }else if(hasNewCredit && collected<=0 && partial>0){
                // New credit with a partial payment and no old receivable collection.
                addCollectionDetailRow(card,"NEW CREDIT",
                        "Box "+qtyText(newBox)+"  |  Reams "+qtyText(newReam));
                addCollectionDetailRow(card,"AMOUNT OF NEW CREDIT",moneyText(newAmount));
                addCollectionDetailRow(card,"PARTIAL",moneyText(partial));
                addCollectionDetailRow(card,"BALANCE",moneyText(displayedBalance));
            }else if(hasNewCredit && collected>0 && partial>0){
                // Existing receivable collection plus a new credit order and partial payment.
                addCollectionDetailRow(card,"QUANTITY (PREVIOUS ORDER)",
                        "Box "+qtyText(oldBox)+"  |  Reams "+qtyText(oldReam));
                addCollectionDetailRow(card,"RECEIVABLE COLLECTION",moneyText(collected));
                addCollectionDetailRow(card,"NEW CREDIT",
                        "Box "+qtyText(newBox)+"  |  Reams "+qtyText(newReam));
                addCollectionDetailRow(card,"AMOUNT OF NEW CREDIT",moneyText(newAmount));
                addCollectionDetailRow(card,"PARTIAL",moneyText(partial));
                addCollectionDetailRow(card,"BALANCE",moneyText(displayedBalance));
            }else if(hasNewCredit && collected>0){
                // Existing receivable collection plus a new credit order without partial payment.
                addCollectionDetailRow(card,"QUANTITY (PREVIOUS ORDER)",
                        "Box "+qtyText(oldBox)+"  |  Reams "+qtyText(oldReam));
                addCollectionDetailRow(card,"RECEIVABLE COLLECTION",moneyText(collected));
                addCollectionDetailRow(card,"NEW CREDIT",
                        "Box "+qtyText(newBox)+"  |  Reams "+qtyText(newReam));
                addCollectionDetailRow(card,"BALANCE",moneyText(newAmount));
            }else if(hasNewCredit){
                // New credit without old receivable collection or partial payment.
                addCollectionDetailRow(card,"NEW CREDIT",
                        "Box "+qtyText(newBox)+"  |  Reams "+qtyText(newReam));
                addCollectionDetailRow(card,"BALANCE",moneyText(newAmount));
            }

            LinearLayout.LayoutParams cardLp=lp(-1,-2);
            cardLp.setMargins(0,0,0,dp(6));
            records.addView(card,cardLp);
        }
        if(groups.size()==0){
            TextView empty=label("No collection or credit records for this day.",14,false);
            empty.setTextColor(TEXT_MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8),dp(14),dp(8),dp(14));
            records.addView(empty,lp(-1,-2));
        }
        addCollectionTotalRow(totalPanel,"TOTAL RECEIVABLE COLLECTION",dayCollection);
        addCollectionTotalRow(totalPanel,"TOTAL NEW CREDIT (BALANCE)",dayNewCreditBalance);
        if(dayPartialPayment>0){
            addCollectionTotalRow(totalPanel,"TOTAL PARTIAL PAYMENT",dayPartialPayment);
        }
    }

    private void addCollectionDetailRow(LinearLayout card,String labelText,String valueText){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView=label(labelText,12,true);labelView.setTextColor(TEXT_MUTED);
        TextView valueView=label(valueText,13,true);valueView.setTextColor(TEXT_MAIN);valueView.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        line.addView(labelView,new LinearLayout.LayoutParams(0,-2,1f));line.addView(valueView,lp(-2,-2));
        LinearLayout.LayoutParams lineLp=lp(-1,-2);lineLp.setMargins(0,dp(2),0,0);card.addView(line,lineLp);
    }

    private void addCollectionTotalRow(LinearLayout panel,String labelText,double amount){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView=label(labelText,11,true);labelView.setTextColor(TEXT_MUTED);
        TextView valueView=label(moneyText(amount),14,true);valueView.setTextColor(NAVY);valueView.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        line.addView(labelView,new LinearLayout.LayoutParams(0,-2,1f));line.addView(valueView,lp(-2,-2));
        LinearLayout.LayoutParams lineLp=lp(-1,-2);lineLp.setMargins(0,dp(3),0,0);panel.addView(line,lineLp);
    }

    private boolean sameRecordDate(String first,String second){
        try{
            SimpleDateFormat fmt=new SimpleDateFormat("dd-MMM-yyyy",Locale.US);
            Date a=fmt.parse(first),b=fmt.parse(second);
            if(a==null||b==null)return false;
            Calendar ca=Calendar.getInstance(),cb=Calendar.getInstance();ca.setTime(a);cb.setTime(b);
            return ca.get(Calendar.YEAR)==cb.get(Calendar.YEAR)&&ca.get(Calendar.DAY_OF_YEAR)==cb.get(Calendar.DAY_OF_YEAR);
        }catch(Exception ignored){return first!=null&&first.equalsIgnoreCase(second);}
    }

    private void showAdvancedDailySalesDialog() {
        final Calendar selectedDate = Calendar.getInstance();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());

        // Rebuild Daily Sales to use the same compact report-card design as Weekly Sales.
        final LinearLayout panel = col();
        panel.setBackground(rounded(CARD_BG, 12, BORDER));
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView dailySalesHeading = label("DISPOSAL", 18, true);
        dailySalesHeading.setTextColor(TEXT_MAIN);
        dailySalesHeading.setGravity(Gravity.CENTER_VERTICAL);
        dailySalesHeading.setPadding(dp(4), 0, dp(4), dp(6));
        panel.addView(dailySalesHeading, lp(-1, 34));

        // Full-width date selector styled like the reference design.
        final Button dateButton = btn("");
        dateButton.setTextColor(TEXT_MAIN);
        // Match the TOTAL SALES action button's full width and 52dp height.
        // Increase only the date label text by 4sp; keep the button dimensions unchanged.
        setSp(dateButton, 16);
        dateButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dateButton.setBackground(rounded(FIELD_BG, 12, BORDER));
        dateButton.setGravity(Gravity.CENTER);
        dateButton.setMinWidth(0);
        dateButton.setMinimumWidth(0);
        dateButton.setMinHeight(0);
        dateButton.setMinimumHeight(0);
        dateButton.setIncludeFontPadding(false);
        dateButton.setPadding(dp(4), 0, dp(4), 0);
        if (android.os.Build.VERSION.SDK_INT >= 21) dateButton.setElevation(0);
        LinearLayout.LayoutParams dateLp = lp(-1, 52);
        dateLp.setMargins(0, dp(8), 0, dp(10));
        panel.addView(dateButton, dateLp);

        // Only the product list scrolls; the daily totals remain visible at the bottom.
        final ScrollView recordScroll = new ScrollView(this);
        recordScroll.setFillViewport(false);
        recordScroll.setVerticalScrollBarEnabled(true);
        recordScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout productRows = col();
        productRows.setPadding(0, dp(2), 0, dp(2));
        recordScroll.addView(productRows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams recordScrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        recordScrollParams.setMargins(0, dp(2), 0, dp(2));
        panel.addView(recordScroll, recordScrollParams);

        final LinearLayout fixedTotal = col();
        fixedTotal.setBackground(rounded(FIELD_BG, 10, BORDER));
        fixedTotal.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams totalParams = lp(-1, -2);
        totalParams.setMargins(0, dp(4), 0, 0);
        panel.addView(fixedTotal, totalParams);

        final Button closeInPanel = btn("CLOSE");
        closeInPanel.setTextColor(Color.WHITE);
        closeInPanel.setBackground(rounded(NAVY_DARK, 12, NAVY_DARK));
        closeInPanel.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-2, dp(42));
        closeParams.gravity = Gravity.RIGHT;
        closeParams.setMargins(0, dp(8), 0, 0);
        panel.addView(closeInPanel, closeParams);

        final Runnable[] update = new Runnable[1];
        update[0] = new Runnable() {
            public void run() {
                final String chosenDate = dateFormat.format(selectedDate.getTime());
                dateButton.setText(tx("📅  ") + chosenDate + "  •  Tap to change date");
                productRows.removeAllViews();
                fixedTotal.removeAllViews();

                java.util.LinkedHashMap<String, double[]> totalsByProduct = new java.util.LinkedHashMap<String, double[]>();
                for (int i = 0; i < names.size(); i++) {
                    String productName = names.get(i);
                    if (productName != null && productName.trim().length() > 0) {
                        totalsByProduct.put(productName, new double[] {0, 0, 0});
                    }
                }

                double totalBoxes = 0, totalReams = 0, totalAmount = 0;
                for (int i = 0; i < sales.size(); i++) {
                    Sale sale = sales.get(i);
                    if (!chosenDate.equals(sale.date)) continue;
                    String rawName = sale.product == null ? "" : sale.product.trim();
                    int inventoryIndex = find(rawName);
                    String productName = inventoryIndex >= 0 ? names.get(inventoryIndex)
                        : (rawName.length() == 0 ? "Unnamed product" : rawName);
                    double[] totals = totalsByProduct.get(productName);
                    if (totals == null) {
                        totals = new double[] {0, 0, 0};
                        totalsByProduct.put(productName, totals);
                    }
                    totals[0] += sale.box;
                    totals[1] += sale.ream;
                    totals[2] += sale.amount();
                    totalBoxes += sale.box;
                    totalReams += sale.ream;
                    totalAmount += sale.amount();
                }

                for (java.util.Map.Entry<String, double[]> entry : totalsByProduct.entrySet()) {
                    double[] totals = entry.getValue();
                    LinearLayout productCard = col();
                    productCard.setBackground(rounded(FIELD_BG, 9, BORDER));
                    productCard.setPadding(dp(10), dp(9), dp(10), dp(9));

                    TextView productName = label(entry.getKey(), 14, true);
                    productName.setTextColor(TEXT_MAIN);
                    productCard.addView(productName, lp(-1, -2));

                    LinearLayout boxLine = row();
                    boxLine.setGravity(Gravity.CENTER_VERTICAL);
                    TextView boxLabel = label("BOX", 12, false);
                    boxLabel.setTextColor(TEXT_MUTED);
                    TextView boxValue = label(qtyText(totals[0]), 13, true);
                    boxValue.setTextColor(TEXT_MAIN);
                    boxValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    boxLine.addView(boxLabel, new LinearLayout.LayoutParams(0, -2, 1));
                    boxLine.addView(boxValue, lp(-2, -2));
                    productCard.addView(boxLine, lp(-1, -2));

                    LinearLayout reamLine = row();
                    reamLine.setGravity(Gravity.CENTER_VERTICAL);
                    TextView reamLabel = label("REAM", 12, false);
                    reamLabel.setTextColor(TEXT_MUTED);
                    TextView reamValue = label(qtyText(totals[1]), 13, true);
                    reamValue.setTextColor(TEXT_MAIN);
                    reamValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    reamLine.addView(reamLabel, new LinearLayout.LayoutParams(0, -2, 1));
                    reamLine.addView(reamValue, lp(-2, -2));
                    productCard.addView(reamLine, lp(-1, -2));

                    LinearLayout.LayoutParams productParams = lp(-1, -2);
                    productParams.setMargins(0, dp(3), 0, dp(3));
                    productRows.addView(productCard, productParams);
                }

                TextView totalHeading = label("DAILY TOTALS", 13, true);
                totalHeading.setTextColor(TEXT_MAIN);
                totalHeading.setPadding(0, 0, 0, dp(5));
                fixedTotal.addView(totalHeading, lp(-1, -2));
                addSalesSummaryLine(fixedTotal, "TOTAL PRODUCTS SOLD",
                    qtyText(totalBoxes) + " box  •  " + qtyText(totalReams) + " ream");
                View totalDivider = new View(MainActivity.this);
                totalDivider.setBackgroundColor(BORDER);
                LinearLayout.LayoutParams dividerParams = lp(-1, dp(1));
                dividerParams.setMargins(0, dp(7), 0, dp(7));
                fixedTotal.addView(totalDivider, dividerParams);
                LinearLayout amountTotalLine = row();
                amountTotalLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView amountTotalLabel = label("TOTAL AMOUNT", 15, true);
                amountTotalLabel.setTextColor(TEXT_MAIN);
                TextView amountTotalValue = label(moneyText(totalAmount), 17, true);
                styleAmountValue(amountTotalValue);
                amountTotalLine.addView(amountTotalLabel, new LinearLayout.LayoutParams(0, -2, 1));
                amountTotalLine.addView(amountTotalValue, lp(-2, -2));
                fixedTotal.addView(amountTotalLine, lp(-1, -2));
            }
        };

        dateButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int pickerTheme = lightTheme ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert;
                DatePickerDialog picker = new DatePickerDialog(MainActivity.this, pickerTheme,
                    new DatePickerDialog.OnDateSetListener() {
                        public void onDateSet(DatePicker view, int year, int month, int day) {
                            selectedDate.set(Calendar.YEAR, year);
                            selectedDate.set(Calendar.MONTH, month);
                            selectedDate.set(Calendar.DAY_OF_MONTH, day);
                            update[0].run();
                        }
                    }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
                showCompactDailySalesDatePicker(picker);
            }
        });

        update[0].run();
        final AlertDialog dialog = new ThemedDialogBuilder().setView(panel).create();
        closeInPanel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });
        dialog.show();
        applyDialogTheme(dialog);
        if (dialog.getWindow() != null) {
            int availableHeight = getResources().getDisplayMetrics().heightPixels - dp(150);
            int dialogHeight = Math.min(availableHeight, dp(600));
            dialog.getWindow().setLayout(
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(520)),
                dialogHeight);
        }
    }

    private void addSalesSummaryLine(LinearLayout parent, String heading, String value) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = label(heading, 12, true);
        left.setTextColor(TEXT_MUTED);
        TextView right = label(value, 14, true);
        right.setTextColor(TEXT_MAIN);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        line.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(right, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(line, lp(-1, -2));
    }
    private void updateSalesListSummary(long selectedMillis) {
        if (salesListSummary == null) return;
        double totalBox = 0, totalReam = 0, totalAmount = 0;
        for (int i = 0; i < sales.size(); i++) {
            Sale sale = sales.get(i);
            if (saleDateMillis(sale.date) == selectedMillis) {
                totalBox += sale.box;
                totalReam += sale.ream;
                totalAmount += sale.amount();
            }
        }
        // Display-only conversion for the selected DISPOSAL day: every 50 reams
        // are shown as 1 box, with the remainder kept as reams. Stored sales and
        // all amount calculations remain unchanged.
        double extraBoxes = Math.floor((totalReam + 0.000000001d) / 50.0d);
        double displayBoxes = totalBox + extraBoxes;
        double displayReams = totalReam - (extraBoxes * 50.0d);
        if (Math.abs(displayReams) < 0.000000001d) displayReams = 0;

        salesListSummary.removeAllViews();
        LinearLayout productsRow = row();
        productsRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView productsLabel = label("TOTAL PRODUCTS SOLD", 12, true);
        productsLabel.setTextColor(TEXT_MUTED);
        TextView productsValue = label("Box: " + qtyText(displayBoxes) + "  |  Ream: " + qtyText(displayReams), 13, true);
        productsValue.setTextColor(TEXT_MAIN);
        productsValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        productsRow.addView(productsLabel, new LinearLayout.LayoutParams(0, dp(30), 1));
        productsRow.addView(productsValue, new LinearLayout.LayoutParams(-2, dp(30)));
        salesListSummary.addView(productsRow, lp(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dividerLp = lp(-1, 1);
        dividerLp.setMargins(0, dp(2), 0, dp(2));
        salesListSummary.addView(divider, dividerLp);

        LinearLayout amountRow = row();
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView amountLabel = label("TOTAL AMOUNT", 12, true);
        amountLabel.setTextColor(TEXT_MUTED);
        TextView amountValue = label(moneyText(totalAmount), 14, true);
        styleAmountValue(amountValue);
        amountRow.addView(amountLabel, new LinearLayout.LayoutParams(0, dp(30), 1));
        amountRow.addView(amountValue, new LinearLayout.LayoutParams(-2, dp(30)));
        salesListSummary.addView(amountRow, lp(-1, -2));
    }

    private void refreshSales() {
        if(salesList==null)return;
        salesList.removeAllViews();
        SimpleDateFormat filterFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        String selectedDate = filterFormat.format(salesListSelectedDate.getTime());
        Calendar selectedDay = (Calendar) salesListSelectedDate.clone();
        selectedDay.set(Calendar.HOUR_OF_DAY, 0);
        selectedDay.set(Calendar.MINUTE, 0);
        selectedDay.set(Calendar.SECOND, 0);
        selectedDay.set(Calendar.MILLISECOND, 0);
        long selectedMillis = selectedDay.getTimeInMillis();
        if (salesHistoryDayButtons != null) {
            int selectedDow = selectedDay.get(Calendar.DAY_OF_WEEK);
            int selectedIndex = selectedDow == Calendar.SUNDAY ? 6 : selectedDow - Calendar.MONDAY;
            for (int i = 0; i < salesHistoryDayButtons.length; i++) {
                Button b = salesHistoryDayButtons[i];
                if (b != null) {
                    boolean active = i == selectedIndex;
                    b.setBackground(rounded(active ? NAVY_DARK : FIELD_BG, 8, active ? NAVY : BORDER));
                    b.setTextColor(active ? Color.WHITE : TEXT_MAIN);
                }
            }
        }

        // Show only records from the selected weekday; weekday buttons stay fixed.
        ArrayList<String> groupKeys=new ArrayList<String>();
        ArrayList<ArrayList<Integer>> groups=new ArrayList<ArrayList<Integer>>();
        for(int i=0;i<sales.size();i++){
            Sale sale=sales.get(i);
            if(saleDateMillis(sale.date)!=selectedMillis) continue;
            String key=sale.date+"\\u0001"+sale.customer.trim().toLowerCase(Locale.ROOT)+"\\u0001"+(sale.address==null?"":sale.address.trim().toLowerCase(Locale.ROOT));
            int g=groupKeys.indexOf(key);
            if(g<0){groupKeys.add(key);groups.add(new ArrayList<Integer>());g=groups.size()-1;}
            groups.get(g).add(i);
        }
        updateSalesListSummary(selectedMillis);
        if(groups.size()==0) {
            TextView empty=label("No sales recorded for " + new SimpleDateFormat("EEEE, dd-MMM-yyyy", Locale.getDefault()).format(salesListSelectedDate.getTime()) + ".",14,false);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(TEXT_MUTED);
            empty.setPadding(dp(8),dp(24),dp(8),dp(24));
            salesList.addView(empty,lp(-1,-2));
            return;
        }
        for(int g=0;g<groups.size();g++){
            final ArrayList<Integer> indexes=groups.get(g);
            // Sales are stored newest-first, so product lines from one saved order
            // appear in reverse entry order. Reverse only this display copy so the
            // first product entered appears first, including records saved previously.
            Collections.reverse(indexes);
            Sale first=sales.get(indexes.get(0));
            LinearLayout card=col();
            styleCard(card, CARD_BG);
            card.setPadding(dp(12),dp(10),dp(12),dp(10));
            LinearLayout customerHeader = row();
            customerHeader.setGravity(Gravity.CENTER_VERTICAL);
            TextView customerHeading=label(first.customer,16,true);
            customerHeading.setTextColor(NAVY); // Match the teal color used for ₱ amounts.
            customerHeader.addView(customerHeading,new LinearLayout.LayoutParams(0,-2,1));
            boolean creditOrder = false;
            for (int k = 0; k < indexes.size(); k++) {
                Sale groupedSale = sales.get(indexes.get(k));
                if (groupedSale != null && groupedSale.credit
                        && groupedSale.product != null
                        && !groupedSale.product.equalsIgnoreCase("Receivable Collection")
                        && !groupedSale.product.startsWith("CHANGE_STOCK::")) {
                    creditOrder = true;
                    break;
                }
            }
            boolean hasNormalTransaction = false;
            for (int k = 0; k < indexes.size(); k++) {
                Sale groupedSale = sales.get(indexes.get(k));
                if (groupedSale != null && groupedSale.product != null
                        && !groupedSale.product.equalsIgnoreCase("Receivable Collection")
                        && !groupedSale.product.startsWith("CHANGE_STOCK::")) {
                    hasNormalTransaction = true;
                    break;
                }
            }
            if (creditOrder || hasNormalTransaction) {
                TextView transactionStatus = label(creditOrder ? "CREDIT" : "CASH",11,true);
                transactionStatus.setTextColor(Color.WHITE);
                transactionStatus.setGravity(Gravity.CENTER);
                transactionStatus.setPadding(dp(8),dp(3),dp(8),dp(3));
                transactionStatus.setBackground(rounded(NAVY_DARK,8,NAVY));
                customerHeader.addView(transactionStatus,lp(-2,-2));
            }
            card.addView(customerHeader,lp(-1,-2));
            if(first.address!=null && first.address.trim().length()>0){TextView addressLine=label(first.address.trim(),13,false);addressLine.setTextColor(TEXT_MUTED);card.addView(addressLine,lp(-1,-2));}
            View separator=new View(MainActivity.this);
            separator.setBackgroundColor(BORDER);
            LinearLayout.LayoutParams separatorLp=lp(-1,1);
            separatorLp.setMargins(0,dp(7),0,dp(6));
            card.addView(separator,separatorLp);
            double totalBox=0,totalReam=0,totalAmount=0;
            for(int j=0;j<indexes.size();j++){
                Sale item=sales.get(indexes.get(j));

                if(item.product!=null && item.product.startsWith("CHANGE_STOCK::")) {
                    String[] parts=item.product.split("::",-1);
                    String fromName=parts.length>1?parts[1]:""; String toName=parts.length>2?parts[2]:"";
                    String boxQty=parts.length>3?parts[3]:"0"; String reamQty=parts.length>4?parts[4]:"0";
                    LinearLayout fromLine=row(); fromLine.setGravity(Gravity.CENTER_VERTICAL);
                    TextView fromLabel=label("FROM",13,true); fromLabel.setTextColor(TEXT_MUTED);
                    TextView fromProduct=label(fromName,14,true); fromProduct.setTextColor(TEXT_MAIN); fromProduct.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                    fromLine.addView(fromLabel,new LinearLayout.LayoutParams(0,-2,1)); fromLine.addView(fromProduct,lp(-2,-2));
                    LinearLayout.LayoutParams fromLp=lp(-1,-2); fromLp.setMargins(0,dp(5),0,0); card.addView(fromLine,fromLp);
                    LinearLayout toLine=row(); toLine.setGravity(Gravity.CENTER_VERTICAL);
                    TextView toLabel=label("TO",13,true); toLabel.setTextColor(TEXT_MUTED);
                    TextView toProduct=label(toName,14,true); toProduct.setTextColor(TEXT_MAIN); toProduct.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                    toLine.addView(toLabel,new LinearLayout.LayoutParams(0,-2,1)); toLine.addView(toProduct,lp(-2,-2));
                    LinearLayout.LayoutParams toLp=lp(-1,-2); toLp.setMargins(0,dp(4),0,0); card.addView(toLine,toLp);
                    TextView qtyLabel=label("Changed quantity: Box " + boxQty + "  |  Ream " + reamQty,12,false); qtyLabel.setTextColor(TEXT_MUTED);
                    LinearLayout.LayoutParams qtyLp=lp(-1,-2); qtyLp.setMargins(0,dp(4),0,0); card.addView(qtyLabel,qtyLp);
                } else {
                    // List each product and its quantity; show the money total once for the complete customer order.
                    LinearLayout productLine=row(); productLine.setGravity(Gravity.CENTER_VERTICAL);
                    TextView productName=label(item.product,14,true); productName.setTextColor(TEXT_MAIN);
                    TextView quantity=label("Box: " + qtyText(item.box) + "  |  Ream: " + qtyText(item.ream),12,true); quantity.setTextColor(TEXT_MAIN); quantity.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    productLine.addView(productName,new LinearLayout.LayoutParams(0,-2,1)); productLine.addView(quantity,new LinearLayout.LayoutParams(-2,-2));
                    LinearLayout.LayoutParams productLineLp=lp(-1,-2); productLineLp.setMargins(0,dp(5),0,0); card.addView(productLine,productLineLp);
                    totalBox+=item.box; totalReam+=item.ream; totalAmount+=item.amount();
                }
            }

            // Show the combined quantity only when this customer order contains
            // two or more product entries. A single-product order needs no duplicate total.
            boolean hasNormalSale=false; for(int j=0;j<indexes.size();j++){Sale grouped=sales.get(indexes.get(j));if(grouped.product==null||!grouped.product.startsWith("CHANGE_STOCK::")){hasNormalSale=true;break;}}
            if(indexes.size() >= 2 && hasNormalSale) {
                View totalDivider=new View(MainActivity.this);
                totalDivider.setBackgroundColor(BORDER);
                LinearLayout.LayoutParams totalDividerLp=lp(-1,1);
                totalDividerLp.setMargins(0,dp(6),0,dp(6));
                card.addView(totalDivider,totalDividerLp);

                LinearLayout quantityTotalLine=row();
                quantityTotalLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView quantityTotalLabel=label("TOTAL ORDER QUANTITY",12,true);
                quantityTotalLabel.setTextColor(TEXT_MUTED);
                TextView quantityTotal=label("Box: "+qtyText(totalBox)+"  |  Ream: "+qtyText(totalReam),12,true);
                quantityTotal.setTextColor(TEXT_MAIN);
                quantityTotal.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                quantityTotalLine.addView(quantityTotalLabel,new LinearLayout.LayoutParams(0,-2,1));
                quantityTotalLine.addView(quantityTotal,new LinearLayout.LayoutParams(-2,-2));
                LinearLayout.LayoutParams quantityTotalLp=lp(-1,-2);
                quantityTotalLp.setMargins(0,dp(2),0,0);
                card.addView(quantityTotalLine,quantityTotalLp);
            }

            if(hasNormalSale) {
                LinearLayout totalLine=row();
                totalLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView totalLabel=label("TOTAL AMOUNT OF ORDER",12,true);
                totalLabel.setTextColor(TEXT_MUTED);
                TextView totalValue=label(moneyText(totalAmount),15,true);
                styleAmountValue(totalValue);
                totalLine.addView(totalLabel,new LinearLayout.LayoutParams(0,-2,1));
                totalLine.addView(totalValue,lp(-2,-2));
                LinearLayout.LayoutParams totalLineLp=lp(-1,-2);
                totalLineLp.setMargins(0,dp(5),0,0);
                card.addView(totalLine,totalLineLp);
            }

            // Show partial payment only when the customer actually paid a partial amount.
            double partialPaid = 0;
            for (int j = 0; j < indexes.size(); j++) partialPaid += sales.get(indexes.get(j)).partial;
            if (partialPaid > 0) {
                LinearLayout partialLine = row();
                partialLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView partialLabel = label("PARTIAL PAYMENT", 12, true);
                partialLabel.setTextColor(TEXT_MUTED);
                TextView partialValue = label(moneyText(partialPaid), 14, true);
                styleAmountValue(partialValue);
                partialLine.addView(partialLabel, new LinearLayout.LayoutParams(0, -2, 1));
                partialLine.addView(partialValue, lp(-2, -2));
                LinearLayout.LayoutParams partialLineLp = lp(-1, -2);
                partialLineLp.setMargins(0, dp(3), 0, 0);
                card.addView(partialLine, partialLineLp);

                // Show the remaining balance for this order only when a partial
                // payment was recorded. Full unpaid orders do not show this row.
                LinearLayout balanceLine = row();
                balanceLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView balanceLabel = label("BALANCE", 12, true);
                balanceLabel.setTextColor(TEXT_MUTED);
                TextView balanceValue = label(moneyText(Math.max(0, totalAmount - partialPaid)), 14, true);
                styleAmountValue(balanceValue);
                balanceLine.addView(balanceLabel, new LinearLayout.LayoutParams(0, -2, 1));
                balanceLine.addView(balanceValue, lp(-2, -2));
                LinearLayout.LayoutParams balanceLineLp = lp(-1, -2);
                balanceLineLp.setMargins(0, dp(3), 0, 0);
                card.addView(balanceLine, balanceLineLp);
            }

            double receivableCollected = 0;
            for (int j = 0; j < indexes.size(); j++) receivableCollected += sales.get(indexes.get(j)).receivable;
            if (receivableCollected > 0) {
                LinearLayout receivableLine = row();
                receivableLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView receivableLabel = label("RECEIVABLE COLLECTION", 12, true);
                receivableLabel.setTextColor(TEXT_MUTED);
                TextView receivableValue = label(moneyText(receivableCollected), 14, true);
                styleAmountValue(receivableValue);
                receivableLine.addView(receivableLabel, new LinearLayout.LayoutParams(0, -2, 1));
                receivableLine.addView(receivableValue, lp(-2, -2));
                LinearLayout.LayoutParams receivableLineLp = lp(-1, -2);
                receivableLineLp.setMargins(0, dp(3), 0, 0);
                card.addView(receivableLine, receivableLineLp);
            }

            LinearLayout.LayoutParams cardParams=lp(-1,-2);
            cardParams.setMargins(0,dp(4),0,dp(4));
            salesList.addView(card,cardParams);
            addPressAnimation(card);
            card.setOnClickListener(new View.OnClickListener(){public void onClick(View v){chooseTransactionItem(indexes);}});
        }
    }

    private void chooseTransactionItem(final ArrayList<Integer> indexes){
        if(indexes.size()==1){editSale(indexes.get(0));return;}
        String[] choices=new String[indexes.size()];
        for(int i=0;i<indexes.size();i++){
            Sale item=sales.get(indexes.get(i));
            choices[i]=item.product+"  |  Box: "+qtyText(item.box)+"  |  Ream: "+qtyText(item.ream);
        }
        new ThemedDialogBuilder().setTitle(tx("Choose product to edit or delete"))
            .setItems(choices,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){editSale(indexes.get(which));}})
            .setNegativeButton(tx("Cancel"),null).show();
    }

    // Parse stored sale dates as calendar dates. Comparing dd-MMM-yyyy strings lexically is unsafe
    // across month/year boundaries (for example 28-Sep and 02-Oct).
    private long saleDateMillis(String value) {
        if (value == null) return Long.MIN_VALUE;
        String[] patterns = {"dd-MMM-yyyy", "d-MMM-yyyy", "dd-MMM-yy", "yyyy-MM-dd"};
        for (int i = 0; i < patterns.length; i++) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(patterns[i], Locale.US);
                format.setLenient(false);
                java.util.Date parsed = format.parse(value.trim());
                if (parsed != null) {
                    Calendar day = Calendar.getInstance();
                    day.setTime(parsed);
                    day.set(Calendar.HOUR_OF_DAY, 0);
                    day.set(Calendar.MINUTE, 0);
                    day.set(Calendar.SECOND, 0);
                    day.set(Calendar.MILLISECOND, 0);
                    return day.getTimeInMillis();
                }
            } catch (Exception ignored) { }
        }
        return Long.MIN_VALUE;
    }

    private boolean saleDateInRange(String saleDate, String startDate, String endDate) {
        long saleMillis = saleDateMillis(saleDate);
        long startMillis = saleDateMillis(startDate);
        long endMillis = saleDateMillis(endDate);
        return saleMillis != Long.MIN_VALUE && startMillis != Long.MIN_VALUE &&
            endMillis != Long.MIN_VALUE && saleMillis >= startMillis && saleMillis <= endMillis;
    }

    private void buildSalesPeriodPage(final boolean weekly) {
        final Calendar todayCal=Calendar.getInstance();
        final LinearLayout dialogContent=col();
        // Use the same outer panel design as Daily Sales: charcoal card, rounded border,
        // and matching inner spacing. Keep the report content itself flat and uncluttered.
        dialogContent.setBackground(rounded(CARD_BG,12,BORDER));
        dialogContent.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView pageTitle=label(weekly?"WEEKLY SALES":"MONTHLY SALES",18,true);
        pageTitle.setTextColor(TEXT_MAIN);
        pageTitle.setGravity(Gravity.CENTER_VERTICAL);
        pageTitle.setPadding(dp(4),0,dp(4),dp(6));
        dialogContent.addView(pageTitle,lp(-1,34));

        final LinearLayout periodTabs=col();
        periodTabs.setGravity(Gravity.CENTER);
        // The selected date range stays visible; the space below is reserved for a new design.
        final TextView fixedDate=label("",15,true);
        fixedDate.setTextColor(TEXT_MAIN);
        fixedDate.setPadding(dp(4),dp(6),dp(4),dp(8));

        // Attach the tab container and date label to the visible dialog.
        // Without these addView calls, the buttons are created but never displayed.
        dialogContent.addView(periodTabs,lp(-1,48));
        dialogContent.addView(fixedDate,lp(-1,-2));

        // Scrollable sales records appear directly below the selected date range.
        final ScrollView recordScroll = new ScrollView(this);
        recordScroll.setFillViewport(false);
        recordScroll.setVerticalScrollBarEnabled(true);
        recordScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout periodRecords = col();
        periodRecords.setPadding(0, dp(2), 0, dp(2));
        recordScroll.addView(periodRecords, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams recordScrollParams = new LinearLayout.LayoutParams(-1, 0, 1f); // Only product rows scroll; summary stays fixed and visible.
        recordScrollParams.setMargins(0, dp(4), 0, dp(2));
        dialogContent.addView(recordScroll, recordScrollParams);

        // Fixed summary beneath the scrollable product totals list.
        final LinearLayout fixedTotal = col();
        fixedTotal.setBackground(rounded(FIELD_BG, 10, BORDER));
        fixedTotal.setPadding(dp(12), dp(10), dp(12), dp(10));
        fixedTotal.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams totalParams = lp(-1, -2);
        totalParams.setMargins(0, dp(4), 0, 0);
        dialogContent.addView(fixedTotal, totalParams);

        // Keep CLOSE inside the same outer background and align it with the summary card.
        final Button closeInPanel = btn("CLOSE");
        closeInPanel.setTextColor(Color.WHITE);
        closeInPanel.setBackground(rounded(NAVY_DARK, 12, NAVY_DARK));
        closeInPanel.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-2, dp(42));
        closeParams.gravity = Gravity.RIGHT;
        closeParams.setMargins(0, dp(8), 0, 0);
        dialogContent.addView(closeInPanel, closeParams);

        final String[] monthNames={"January","February","March","April","May","June",
            "July","August","September","October","November","December"};
        // Week 1 includes the short days at the start of the month and runs through Sunday
        // of the week containing the first Monday. Following weeks run Monday-Sunday;
        // the final week stops at month-end.
        Calendar countMonthFirst = Calendar.getInstance();
        countMonthFirst.setTime(todayCal.getTime());
        countMonthFirst.set(Calendar.DAY_OF_MONTH, 1);
        int daysInSelectedMonth = countMonthFirst.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDayOfWeek = countMonthFirst.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2
        int daysUntilFirstMonday = (Calendar.MONDAY - firstDayOfWeek + 7) % 7;
        final int firstMondayDay = 1 + daysUntilFirstMonday;
        final int firstWeekEndDay = Math.min(daysInSelectedMonth, firstMondayDay + 6);
        int remainingDaysAfterWeek1 = Math.max(0, daysInSelectedMonth - firstWeekEndDay);
        final int weekCount = 1 + (remainingDaysAfterWeek1 + 6) / 7;
        final String[] weekNames = new String[weekCount];
        for (int weekIndex = 0; weekIndex < weekCount; weekIndex++) {
            weekNames[weekIndex] = "Week " + (weekIndex + 1);
        }
        final Button[] tabButtons=new Button[weekly?weekCount:12];

        final Runnable[] refreshPeriodRecords=new Runnable[1];
        refreshPeriodRecords[0]=new Runnable() {
            public void run() {
                int selected=0;
                for(int i=0;i<tabButtons.length;i++) {
                    if(tabButtons[i].isSelected()) { selected=i; break; }
                }

                Calendar from=Calendar.getInstance();
                Calendar to=Calendar.getInstance();
                Calendar periodStart=Calendar.getInstance();
                Calendar periodEnd=Calendar.getInstance();
                if(!weekly) {
                    // Monthly Sales: selected calendar month, from its first through its last day.
                    periodStart.setTime(todayCal.getTime());
                    periodStart.set(Calendar.MONTH,selected);
                    periodStart.set(Calendar.DAY_OF_MONTH,1);
                    periodStart.set(Calendar.HOUR_OF_DAY,0);
                    periodStart.set(Calendar.MINUTE,0);
                    periodStart.set(Calendar.SECOND,0);
                    periodStart.set(Calendar.MILLISECOND,0);
                    periodEnd.setTime(periodStart.getTime());
                    periodEnd.set(Calendar.DAY_OF_MONTH,periodEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
                    from.setTime(periodStart.getTime());
                    to.setTime(periodEnd.getTime());
                } else {
                    // Week 1 includes the short days at the start of the month through Sunday.
                    // Example: if the first Monday is October 5, October 1-4 roll into
                    // the week of October 5-11 (Week 1 is October 1-11). Following weeks run Monday-Sunday.
                    Calendar monthFirst = Calendar.getInstance();
                    monthFirst.setTime(todayCal.getTime());
                    monthFirst.set(Calendar.DAY_OF_MONTH, 1);
                    monthFirst.set(Calendar.HOUR_OF_DAY, 0);
                    monthFirst.set(Calendar.MINUTE, 0);
                    monthFirst.set(Calendar.SECOND, 0);
                    monthFirst.set(Calendar.MILLISECOND, 0);
                    int firstDayOfWeek = monthFirst.get(Calendar.DAY_OF_WEEK);
                    int daysUntilFirstMonday = (Calendar.MONDAY - firstDayOfWeek + 7) % 7;
                    Calendar firstMonday = Calendar.getInstance();
                    firstMonday.setTime(monthFirst.getTime());
                    firstMonday.add(Calendar.DAY_OF_MONTH, daysUntilFirstMonday);
                    if (selected == 0) {
                        periodStart.setTime(monthFirst.getTime());
                        periodEnd.setTime(firstMonday.getTime());
                        periodEnd.add(Calendar.DAY_OF_MONTH, 6);
                    } else {
                        periodStart.setTime(firstMonday.getTime());
                        periodStart.add(Calendar.DAY_OF_MONTH, selected * 7);
                        periodEnd.setTime(periodStart.getTime());
                        periodEnd.add(Calendar.DAY_OF_MONTH, 6);
                    }
                    Calendar monthLast = Calendar.getInstance();
                    monthLast.setTime(monthFirst.getTime());
                    monthLast.set(Calendar.DAY_OF_MONTH, monthLast.getActualMaximum(Calendar.DAY_OF_MONTH));
                    if (periodEnd.after(monthLast)) {
                        periodEnd.setTime(monthLast.getTime());
                    }
                    from.setTime(periodStart.getTime());
                    to.setTime(periodEnd.getTime());
                }

                SimpleDateFormat keyFormat=new SimpleDateFormat("dd-MMM-yyyy",Locale.getDefault());
                String startDate=keyFormat.format(from.getTime());
                String endDate=keyFormat.format(to.getTime());
                String heading=weekly?weekNames[selected]+" ("+startDate+" to "+endDate+")":
                    monthNames[selected]+" "+todayCal.get(Calendar.YEAR)+" ("+startDate+" to "+endDate+")";
                fixedDate.setText(heading);

                // Weekly Sales: show every inventory product and its total sold
                // for the selected Monday-Saturday week range, including early-month carry-over days in Week 1.
                periodRecords.removeAllViews();
                fixedTotal.removeAllViews();
                java.util.LinkedHashMap<String, double[]> productTotals = new java.util.LinkedHashMap<String, double[]>();
                for (int inventoryIndex = 0; inventoryIndex < names.size(); inventoryIndex++) {
                    String inventoryName = names.get(inventoryIndex);
                    if (inventoryName != null && inventoryName.trim().length() > 0) {
                        productTotals.put(inventoryName, new double[] {0, 0, 0});
                    }
                }
                double totalBoxes = 0, totalReams = 0, totalAmount = 0;
                for (int saleIndex = 0; saleIndex < sales.size(); saleIndex++) {
                    Sale sale = sales.get(saleIndex);
                    if (!saleDateInRange(sale.date, startDate, endDate)) continue;
                    String rawName = sale.product == null ? "" : sale.product.trim();
                    int inventoryIndex = find(rawName);
                    String productName = inventoryIndex >= 0 ? names.get(inventoryIndex)
                        : (rawName.length() == 0 ? "Unnamed product" : rawName);
                    double[] totals = productTotals.get(productName);
                    if (totals == null) {
                        totals = new double[] {0, 0, 0};
                        productTotals.put(productName, totals);
                    }
                    totals[0] += sale.box;
                    totals[1] += sale.ream;
                    totals[2] += sale.amount();
                    totalBoxes += sale.box;
                    totalReams += sale.ream;
                    totalAmount += sale.amount();
                }

                for (java.util.Map.Entry<String, double[]> entry : productTotals.entrySet()) {
                    double[] totals = entry.getValue();
                    // Display-only conversion: every 50 reams is shown as 1 box.
                    // Keep the original sales records and amount calculations unchanged.
                    double extraBoxes = Math.floor((totals[1] + 0.000000001d) / 50.0d);
                    double displayBoxes = totals[0] + extraBoxes;
                    double displayReams = totals[1] - (extraBoxes * 50.0d);
                    if (Math.abs(displayReams) < 0.000000001d) displayReams = 0;
                    LinearLayout productCard = col();
                    productCard.setBackground(rounded(FIELD_BG, 9, BORDER));
                    productCard.setPadding(dp(10), dp(9), dp(10), dp(9));

                    TextView productName = label(entry.getKey(), 14, true);
                    productName.setTextColor(TEXT_MAIN);
                    productCard.addView(productName, lp(-1, -2));

                    // Product name first, then Box and Ream on separate quantity rows.
                    LinearLayout boxLine = row();
                    boxLine.setGravity(Gravity.CENTER_VERTICAL);
                    TextView boxLabel = label("BOX", 12, false);
                    boxLabel.setTextColor(TEXT_MUTED);
                    TextView boxValue = label(qtyText(displayBoxes), 13, true);
                    boxValue.setTextColor(TEXT_MAIN);
                    boxValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    boxLine.addView(boxLabel, new LinearLayout.LayoutParams(0, -2, 1));
                    boxLine.addView(boxValue, lp(-2, -2));
                    LinearLayout.LayoutParams boxLineParams = lp(-1, -2);
                    boxLineParams.setMargins(0, dp(3), 0, 0);
                    productCard.addView(boxLine, boxLineParams);

                    LinearLayout reamLine = row();
                    reamLine.setGravity(Gravity.CENTER_VERTICAL);
                    TextView reamLabel = label("REAM", 12, false);
                    reamLabel.setTextColor(TEXT_MUTED);
                    TextView reamValue = label(qtyText(displayReams), 13, true);
                    reamValue.setTextColor(TEXT_MAIN);
                    reamValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    reamLine.addView(reamLabel, new LinearLayout.LayoutParams(0, -2, 1));
                    reamLine.addView(reamValue, lp(-2, -2));
                    productCard.addView(reamLine, lp(-1, -2));

                    LinearLayout.LayoutParams productParams = lp(-1, -2);
                    productParams.setMargins(0, dp(3), 0, dp(3));
                    periodRecords.addView(productCard, productParams);
                }

                TextView totalHeading = label(weekly ? "WEEKLY TOTALS" : "MONTHLY TOTALS", 13, true);
                totalHeading.setTextColor(TEXT_MAIN);
                totalHeading.setPadding(0, 0, 0, dp(5));
                fixedTotal.addView(totalHeading, lp(-1, -2));
                // Normalize the combined summary using the same 50-reams-per-box rule.
                double totalExtraBoxes = Math.floor((totalReams + 0.000000001d) / 50.0d);
                double displayTotalBoxes = totalBoxes + totalExtraBoxes;
                double displayTotalReams = totalReams - (totalExtraBoxes * 50.0d);
                if (Math.abs(displayTotalReams) < 0.000000001d) displayTotalReams = 0;
                addSalesSummaryLine(fixedTotal, "TOTAL PRODUCTS SOLD", qtyText(displayTotalBoxes) + " box  •  " + qtyText(displayTotalReams) + " ream");

                // Prominent total amount for the selected week/month.
                View totalDivider = new View(MainActivity.this);
                totalDivider.setBackgroundColor(BORDER);
                LinearLayout.LayoutParams totalDividerParams = lp(-1, dp(1));
                totalDividerParams.setMargins(0, dp(7), 0, dp(7));
                fixedTotal.addView(totalDivider, totalDividerParams);
                LinearLayout overallSalesLine = row();
                overallSalesLine.setGravity(Gravity.CENTER_VERTICAL);
                TextView overallSalesLabel = label("TOTAL AMOUNT", 15, true);
                overallSalesLabel.setTextColor(TEXT_MAIN);
                TextView overallSalesValue = label(moneyText(totalAmount), 17, true);
                styleAmountValue(overallSalesValue);
                overallSalesLine.addView(overallSalesLabel, new LinearLayout.LayoutParams(0, -2, 1));
                overallSalesLine.addView(overallSalesValue, lp(-2, -2));
                fixedTotal.addView(overallSalesLine, lp(-1, -2));
            }
        };

        if(!weekly) {
            // Four month buttons are visible at a time; swipe horizontally for the rest.
            final HorizontalScrollView monthScroll = new HorizontalScrollView(this);
            monthScroll.setHorizontalScrollBarEnabled(false);
            monthScroll.setFillViewport(false);
            monthScroll.setSmoothScrollingEnabled(true);
            monthScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

            LinearLayout monthStrip = row();
            monthStrip.setGravity(Gravity.CENTER_VERTICAL);
            float density = getResources().getDisplayMetrics().density;
            int screenWidthDp = (int)(getResources().getDisplayMetrics().widthPixels / density);
            int monthButtonWidth = dp(Math.max(64, screenWidthDp / 4 - 8));

            for(int i=0;i<monthNames.length;i++) {
                final int tabIndex=i;
                Button monthButton=btn(monthNames[i].substring(0,3));
                setSp(monthButton, 12);
                monthButton.setSingleLine(true);
                monthButton.setTextColor(TEXT_MAIN);
                monthButton.setPadding(dp(2),0,dp(2),0);
                monthButton.setMinWidth(0);
                monthButton.setMinimumWidth(0);
                monthButton.setBackground(rounded(CARD_BG,10,BORDER));
                LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(monthButtonWidth,dp(40));
                tabParams.setMargins(dp(2),dp(2),dp(2),dp(2));
                monthStrip.addView(monthButton,tabParams);
                tabButtons[tabIndex]=monthButton;
                monthButton.setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v) {
                        for(int j=0;j<tabButtons.length;j++) {
                            boolean selected=j==tabIndex;
                            tabButtons[j].setSelected(selected);
                            tabButtons[j].setTextColor(selected?Color.WHITE:TEXT_MAIN);
                            tabButtons[j].setBackground(rounded(selected?NAVY_DARK:CARD_BG,10,selected?NAVY:BORDER));
                        }
                        refreshPeriodRecords[0].run();
                    }
                });
            }
            monthScroll.addView(monthStrip,new HorizontalScrollView.LayoutParams(-2,-1));
            periodTabs.addView(monthScroll,lp(-1,-1));
        } else {
            // Place only the required week buttons in one horizontal row.
            // Week 5 (and Week 6 in a rare calendar layout) appears only when needed.
            LinearLayout weekButtonRow = row();
            weekButtonRow.setGravity(Gravity.CENTER_VERTICAL);
            for(int i=0;i<tabButtons.length;i++) {
                final int tabIndex=i;
                tabButtons[i]=btn(weekNames[i]);
                setSp(tabButtons[i], 11);
                tabButtons[i].setSingleLine(true);
                tabButtons[i].setTextColor(TEXT_MAIN);
                tabButtons[i].setPadding(dp(2),0,dp(2),0);
                tabButtons[i].setMinWidth(0);
                tabButtons[i].setMinimumWidth(0);
                tabButtons[i].setBackground(rounded(CARD_BG,12,BORDER));
                LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(0,dp(40),1);
                tabParams.setMargins(dp(2),dp(2),dp(2),dp(2));
                weekButtonRow.addView(tabButtons[i],tabParams);
                tabButtons[i].setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v) {
                        for(int j=0;j<tabButtons.length;j++) {
                            boolean selected=j==tabIndex;
                            tabButtons[j].setSelected(selected);
                            tabButtons[j].setTextColor(selected?Color.WHITE:TEXT_MAIN);
                            tabButtons[j].setBackground(rounded(selected?NAVY_DARK:CARD_BG,12,selected?NAVY:BORDER));
                        }
                        refreshPeriodRecords[0].run();
                    }
                });
            }
            periodTabs.addView(weekButtonRow,lp(-1,-1));
        }

        int initial;
        if (weekly) {
            Calendar firstOfMonth = Calendar.getInstance();
            firstOfMonth.setTime(todayCal.getTime());
            firstOfMonth.set(Calendar.DAY_OF_MONTH, 1);
            int initialFirstDayOfWeek = firstOfMonth.get(Calendar.DAY_OF_WEEK);
            int initialDaysUntilFirstMonday = (Calendar.MONDAY - initialFirstDayOfWeek + 7) % 7;
            int initialFirstMondayDay = 1 + initialDaysUntilFirstMonday;
            int initialFirstWeekEndDay = initialFirstMondayDay + 6;
            int todayDay = todayCal.get(Calendar.DAY_OF_MONTH);
            if (todayDay <= initialFirstWeekEndDay) {
                initial = 0;
            } else {
                initial = 1 + (todayDay - (initialFirstMondayDay + 7)) / 7;
                initial = Math.max(1, Math.min(weekCount - 1, initial));
            }
        } else {
            initial = todayCal.get(Calendar.MONTH);
        }
        tabButtons[initial].performClick();
        if(!weekly) {
            View monthScrollView = periodTabs.getChildAt(0);
            if(monthScrollView instanceof HorizontalScrollView) {
                final HorizontalScrollView monthScroll = (HorizontalScrollView)monthScrollView;
                final Button selectedMonth = tabButtons[initial];
                monthScroll.post(new Runnable() {
                    public void run() {
                        monthScroll.smoothScrollTo(Math.max(0, selectedMonth.getLeft() - dp(2)), 0);
                    }
                });
            }
        }

        final AlertDialog periodDialog = new ThemedDialogBuilder()
            .setView(dialogContent)
            .create();
        closeInPanel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { periodDialog.dismiss(); }
        });
        periodDialog.show();
        applyDialogTheme(periodDialog);
        if(periodDialog.getWindow()!=null) {
            // Keep the dialog compact and reduce the unused area below the rounded panel.
            // Product rows can scroll; the totals panel and CLOSE button remain fixed.
            int availableHeight = getResources().getDisplayMetrics().heightPixels - dp(150);
            int dialogHeight = Math.min(availableHeight, dp(600));
            periodDialog.getWindow().setLayout(
                Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(520)),
                dialogHeight);
        }
    }

    private void editSale(final int idx) {
        if(idx<0||idx>=sales.size())return;final Sale old=sales.get(idx);final LinearLayout form=col();
        final EditText c=edit("Customer",false);capitalizeFirstInputLetter(c);c.setText(old.customer);final EditText p=edit("Product name",false);capitalizeFirstInputLetter(p);p.setText(old.product);
        final EditText b=edit("Boxes",true);b.setText(qtyText(old.box));final EditText r=edit("Reams",true);r.setText(qtyText(old.ream));
        form.setPadding(dp(12),dp(8),dp(12),dp(8));
        form.setBackgroundColor(CARD_BG);
        TextView customerLabel=label("Customer",14,true);customerLabel.setTextColor(TEXT_MAIN);form.addView(customerLabel);form.addView(c,lp(-1,48));
        TextView productLabel=label("Product name",14,true);productLabel.setTextColor(TEXT_MAIN);form.addView(productLabel);form.addView(p,lp(-1,48));
        TextView boxesLabel=label("Boxes",14,true);boxesLabel.setTextColor(TEXT_MAIN);form.addView(boxesLabel);form.addView(b,lp(-1,48));
        TextView reamsLabel=label("Reams",14,true);reamsLabel.setTextColor(TEXT_MAIN);form.addView(reamsLabel);form.addView(r,lp(-1,48));
        final AlertDialog dialog=new ThemedDialogBuilder().setTitle(tx("Edit sales record")).setView(form).setNegativeButton(tx("Cancel"),null)
        .setNeutralButton(tx("Delete sale"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){deleteSale(idx);}})
        .setPositiveButton(tx("Save changes"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            String nc=c.getText().toString().trim(),np=p.getText().toString().trim();double nb,nr;try{nb=qty(b.getText().toString());nr=qty(r.getText().toString());}catch(Exception ex){Toast.makeText(MainActivity.this,tx("Invalid quantity"),Toast.LENGTH_SHORT).show();return;}
            int oldp=find(old.product);if(oldp>=0){boxes.set(oldp,boxes.get(oldp)+old.box);reams.set(oldp,reams.get(oldp)+old.ream);}
            int newp=find(np);if(nc.length()==0||newp<0||nb>boxes.get(newp)||nr>reams.get(newp)){
                if(oldp>=0){boxes.set(oldp,boxes.get(oldp)-old.box);reams.set(oldp,reams.get(oldp)-old.ream);}
                Toast.makeText(MainActivity.this,tx("Check customer, product and available stock"),Toast.LENGTH_LONG).show();return;
            }
            boxes.set(newp,boxes.get(newp)-nb);reams.set(newp,reams.get(newp)-nr);old.customer=nc;old.product=np;old.box=nb;old.ream=nr;save();showTab(2);
        }}).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){
            applyDialogTheme(dialog);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(CARD_BG));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_MAIN);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(TEXT_MAIN);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(TEXT_MAIN);
            setSp(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), 15);
            setSp(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), 15);
            setSp(dialog.getButton(AlertDialog.BUTTON_POSITIVE), 15);
        }});
        dialog.show();
    }
    private void deleteSale(final int idx) {
        new ThemedDialogBuilder().setTitle(tx("Delete sale?")).setMessage(tx("The sold quantities will be returned to inventory."))
        .setNegativeButton(tx("Cancel"),null).setPositiveButton(tx("Delete"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            Sale s=sales.get(idx);int p=find(s.product);if(p>=0){boxes.set(p,boxes.get(p)+s.box);reams.set(p,reams.get(p)+s.ream);}sales.remove(idx);save();showTab(2);
        }}).show();
    }
}
