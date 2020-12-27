

   1 /*
   2  * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
   3  * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
   4  *
   5  * This code is free software; you can redistribute it and/or modify it
   6  * under the terms of the GNU General Public License version 2 only, as
   7  * published by the Free Software Foundation.  Oracle designates this
   8  * particular file as subject to the "Classpath" exception as provided
   9  * by Oracle in the LICENSE file that accompanied this code.
  10  *
  11  * This code is distributed in the hope that it will be useful, but WITHOUT
  12  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  13  * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
  14  * version 2 for more details (a copy is included in the LICENSE file that
  15  * accompanied this code).
  16  *
  17  * You should have received a copy of the GNU General Public License version
  18  * 2 along with this work; if not, write to the Free Software Foundation,
  19  * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
  20  *
  21  * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
  22  * or visit www.oracle.com if you need additional information or have any
  23  * questions.
  24  */
  25 
  26 package com.sun.javafx.application;
  27 
  28 import com.sun.javafx.PlatformUtil;
  29 import com.sun.javafx.css.StyleManager;
  30 import com.sun.javafx.runtime.SystemProperties;
  31 
  32 import java.lang.reflect.InvocationTargetException;
  33 import java.lang.reflect.Method;
  34 import java.security.AccessControlContext;
  35 import java.util.List;
  36 import java.util.Set;
  37 import java.util.concurrent.CopyOnWriteArraySet;
  38 import java.util.concurrent.CountDownLatch;
  39 import java.util.concurrent.atomic.AtomicBoolean;
  40 import java.util.concurrent.atomic.AtomicInteger;
  41 
  42 import javafx.application.Application;
  43 import javafx.application.ConditionalFeature;
  44 
  45 import com.sun.javafx.tk.TKListener;
  46 import com.sun.javafx.tk.TKStage;
  47 import com.sun.javafx.tk.Toolkit;
  48 import java.security.AccessController;
  49 import java.security.PrivilegedAction;
  50 
  51 public class PlatformImpl {
  52 
  53     private static AtomicBoolean initialized = new AtomicBoolean(false);
  54     private static AtomicBoolean platformExit = new AtomicBoolean(false);
  55     private static AtomicBoolean toolkitExit = new AtomicBoolean(false);
  56     private static CountDownLatch startupLatch = new CountDownLatch(1);
  57     private static AtomicBoolean listenersRegistered = new AtomicBoolean(false);
  58     private static TKListener toolkitListener = null;
  59     private static volatile boolean implicitExit = true;
  60     private static boolean taskbarApplication = true;
  61     private static AtomicInteger pendingRunnables = new AtomicInteger(0);
  62     private static AtomicInteger numWindows = new AtomicInteger(0);
  63     private static volatile boolean firstWindowShown = false;
  64     private static volatile boolean lastWindowClosed = false;
  65     private static AtomicBoolean reallyIdle = new AtomicBoolean(false);
  66     private static Set<FinishListener> finishListeners =
  67             new CopyOnWriteArraySet<FinishListener>();
  68     private final static Object runLaterLock = new Object();
  69     private static Boolean isGraphicsSupported;
  70     private static Boolean isControlsSupported;
  71     private static Boolean isWebSupported;
  72     private static Boolean isSWTSupported;
  73     private static Boolean isSwingSupported;
  74     private static Boolean isFXMLSupported;
  75     private static Boolean hasTwoLevelFocus;
  76     private static Boolean hasVirtualKeyboard;
  77     private static Boolean hasTouch;
  78     private static Boolean hasMultiTouch;
  79     private static Boolean hasPointer;
  80     private static boolean isThreadMerged = false;
  81 
  82     /**
  83      * Set a flag indicating whether this application should show up in the
  84      * task bar. The default value is true.
  85      *
  86      * @param taskbarApplication the new value of this attribute
  87      */
  88     public static void setTaskbarApplication(boolean taskbarApplication) {
  89         PlatformImpl.taskbarApplication = taskbarApplication;
  90     }
  91 
  92     /**
  93      * Returns the current value of the taskBarApplication flag.
  94      *
  95      * @return the current state of the flag.
  96      */
  97     public static boolean isTaskbarApplication() {
  98         return taskbarApplication;
  99     }
 100 
 101     /**
 102      * This method is invoked typically on the main thread. At this point,
 103      * the JavaFX Application Thread has not been started. Any attempt
 104      * to call startup twice results in an exception.
 105      * @param r
 106      */
 107     public static void startup(final Runnable r) {
 108 
 109         // NOTE: if we ever support re-launching an application and/or
 110         // launching a second application in the same VM/classloader
 111         // this will need to be changed.
 112         if (platformExit.get()) {
 113             throw new IllegalStateException("Platform.exit has been called");
 114         }
 115 
 116         if (initialized.getAndSet(true)) {
 117             // If we've already initialized, just put the runnable on the queue.
 118             runLater(r);
 119             return;
 120         }
 121         AccessController.doPrivileged(new PrivilegedAction<Void>() {
 122             @Override public Void run() {
 123                 String s = System.getProperty("com.sun.javafx.twoLevelFocus");
 124                 if (s != null) {
 125                     hasTwoLevelFocus = Boolean.valueOf(s);
 126                 }
 127                 s = System.getProperty("com.sun.javafx.virtualKeyboard");
 128                 if (s != null) {
 129                     if (s.equalsIgnoreCase("none")) {
 130                         hasVirtualKeyboard = false;
 131                     } else if (s.equalsIgnoreCase("javafx")) {
 132                         hasVirtualKeyboard = true;
 133                     } else if (s.equalsIgnoreCase("native")) {
 134                         hasVirtualKeyboard = true;
 135                     }
 136                 }
 137                 s = System.getProperty("com.sun.javafx.touch");
 138                 if (s != null) {
 139                     hasTouch = Boolean.valueOf(s);
 140                 }
 141                 s = System.getProperty("com.sun.javafx.multiTouch");
 142                 if (s != null) {
 143                     hasMultiTouch = Boolean.valueOf(s);
 144                 }
 145                 s = System.getProperty("com.sun.javafx.pointer");
 146                 if (s != null) {
 147                     hasPointer = Boolean.valueOf(s);
 148                 }
 149                 s = System.getProperty("javafx.embed.singleThread");
 150                 if (s != null) {
 151                     isThreadMerged = Boolean.valueOf(s);
 152                 }
 153                 return null;
 154             }
 155         });
 156 
 157         if (!taskbarApplication) {
 158             AccessController.doPrivileged(new PrivilegedAction<Void>() {
 159                 @Override public Void run() {
 160                     System.setProperty("glass.taskbarApplication", "false");
 161                     return null;
 162                 }
 163             });
 164         }
 165 
 166         // Create Toolkit listener and register it with the Toolkit.
 167         // Call notifyFinishListeners when we get notified.
 168         toolkitListener = new TKListener() {
 169             @Override public void changedTopLevelWindows(List<TKStage> windows) {
 170                 numWindows.set(windows.size());
 171                 checkIdle();
 172             }
 173         };
 174         Toolkit.getToolkit().addTkListener(toolkitListener);
 175 
 176         Toolkit.getToolkit().startup(new Runnable() {
 177             @Override public void run() {
 178                 startupLatch.countDown();
 179                 r.run();
 180             }
 181         });
 182 
 183         //Initialize the thread merging mechanism
 184         if (isThreadMerged) {
 185             //Use reflection in case we are running compact profile
 186             try {
 187                 Class swingFXUtilsClass = Class.forName("javafx.embed.swing.SwingFXUtils");
 188                 Method installFwEventQueue = swingFXUtilsClass.getMethod("installFwEventQueue");
 189 
 190                 waitForStart();
 191                 installFwEventQueue.invoke(null);
 192 
 193             } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
 194                 throw new RuntimeException("Property javafx.embed.singleThread is not supported");
 195             } catch (InvocationTargetException e) {
 196                 throw new RuntimeException(e);
 197             }
 198         }
 199     }
 200 
 201     private static void waitForStart() {
 202         // If the startup runnable has not yet been called, then wait it.
 203         // Note that we check the count before calling await() to avoid
 204         // the try/catch which is unnecessary after startup.
 205         if (startupLatch.getCount() > 0) {
 206             try {
 207                 startupLatch.await();
 208             } catch (InterruptedException ex) {
 209                 ex.printStackTrace();
 210             }
 211         }
 212     }
 213 
 214     public static boolean isFxApplicationThread() {
 215         return Toolkit.getToolkit().isFxUserThread();
 216     }
 217 
 218     public static void runLater(final Runnable r) {
 219         runLater(r, false);
 220     }
 221 
 222     private static void runLater(final Runnable r, boolean exiting) {
 223         if (!initialized.get()) {
 224             throw new IllegalStateException("Toolkit not initialized");
 225         }
 226 
 227         pendingRunnables.incrementAndGet();
 228         waitForStart();
 229 
 230         if (SystemProperties.isDebug()) {
 231             Toolkit.getToolkit().pauseCurrentThread();
 232         }
 233 
 234         synchronized (runLaterLock) {
 235             if (!exiting && toolkitExit.get()) {
 236                 // Don't schedule a runnable after we have exited the toolkit
 237                 pendingRunnables.decrementAndGet();
 238                 return;
 239             }
 240 
 241             final AccessControlContext acc = AccessController.getContext();
 242             // Don't catch exceptions, they are handled by Toolkit.defer()
 243             Toolkit.getToolkit().defer(new Runnable() {
 244                 @Override public void run() {
 245                     try {
 246                         AccessController.doPrivileged(new PrivilegedAction<Void>() {
 247                             @Override
 248                             public Void run() {
 249                                 r.run();
 250                                 return null;
 251                             }
 252                         }, acc);
 253                     } finally {
 254                         pendingRunnables.decrementAndGet();
 255                         checkIdle();
 256                     }
 257                 }
 258             });
 259         }
 260     }
 261 
 262     public static void runAndWait(final Runnable r) {
 263         runAndWait(r, false);
 264     }
 265 
 266     private static void runAndWait(final Runnable r, boolean exiting) {
 267         if (SystemProperties.isDebug()) {
 268             Toolkit.getToolkit().pauseCurrentThread();
 269         }
 270 
 271         if (isFxApplicationThread()) {
 272              try {
 273                  r.run();
 274              } catch (Throwable t) {
 275                  System.err.println("Exception in runnable");
 276                  t.printStackTrace();
 277              }
 278         } else {
 279             final CountDownLatch doneLatch = new CountDownLatch(1);
 280             runLater(new Runnable() {
 281                 @Override public void run() {
 282                     try {
 283                         r.run();
 284                     } finally {
 285                         doneLatch.countDown();
 286                     }
 287                 }
 288             }, exiting);
 289 
 290             if (!exiting && toolkitExit.get()) {
 291                 throw new IllegalStateException("Toolkit has exited");
 292             }
 293 
 294             try {
 295                 doneLatch.await();
 296             } catch (InterruptedException ex) {
 297                 ex.printStackTrace();
 298             }
 299         }
 300     }
 301 
 302     public static void setImplicitExit(boolean implicitExit) {
 303         PlatformImpl.implicitExit = implicitExit;
 304         checkIdle();
 305     }
 306 
 307     public static boolean isImplicitExit() {
 308         return implicitExit;
 309     }
 310 
 311     public static void addListener(FinishListener l) {
 312         listenersRegistered.set(true);
 313         finishListeners.add(l);
 314     }
 315 
 316     public static void removeListener(FinishListener l) {
 317         finishListeners.remove(l);
 318         listenersRegistered.set(!finishListeners.isEmpty());
 319     }
 320 
 321     private static void notifyFinishListeners(boolean exitCalled) {
 322         for (FinishListener l : finishListeners) {
 323             if (exitCalled) {
 324                 l.exitCalled();
 325             } else {
 326                 l.idle(implicitExit);
 327             }
 328         }
 329     }
 330 
 331     // Check for idle, meaning the last top-level window has been closed and
 332     // there are no pending Runnables waiting to be run.
 333     private static void checkIdle() {
 334         boolean doNotify = false;
 335 
 336         synchronized (PlatformImpl.class) {
 337             int numWin = numWindows.get();
 338             if (numWin > 0) {
 339                 firstWindowShown = true;
 340                 lastWindowClosed = false;
 341                 reallyIdle.set(false);
 342             } else if (numWin == 0 && firstWindowShown) {
 343                 lastWindowClosed = true;
 344             }
 345 
 346             // In case there is an event in process, allow for it to show
 347             // another window. If no new window is shown before all pending
 348             // runnables (including this one) are done, then we will shutdown.
 349             if (lastWindowClosed && pendingRunnables.get() == 0) {
 350 //                System.err.println("Last window closed and no pending runnables");
 351                 if (reallyIdle.getAndSet(true)) {
 352 //                    System.err.println("Really idle now");
 353                     doNotify = true;
 354                     lastWindowClosed = false;
 355                 } else {
 356 //                    System.err.println("Queuing up a dummy idle check runnable");
 357                     runLater(new Runnable() {
 358                         @Override public void run() {
 359 //                            System.err.println("Dummy runnable");
 360                         }
 361                     });
 362                 }
 363             }
 364         }
 365 
 366         if (doNotify) {
 367             notifyFinishListeners(false);
 368         }
 369     }
 370 
 371     // package scope method for testing
 372     private static final CountDownLatch platformExitLatch = new CountDownLatch(1);
 373     static CountDownLatch test_getPlatformExitLatch() {
 374         return platformExitLatch;
 375     }
 376 
 377     public static void tkExit() {
 378         if (toolkitExit.getAndSet(true)) {
 379             return;
 380         }
 381 
 382         if (initialized.get()) {
 383             // Always call toolkit exit on FX app thread
 384 //            System.err.println("PlatformImpl.tkExit: scheduling Toolkit.exit");
 385             PlatformImpl.runAndWait(new Runnable() {
 386                 @Override public void run() {
 387 //                    System.err.println("PlatformImpl.tkExit: calling Toolkit.exit");
 388                     Toolkit.getToolkit().exit();
 389                 }
 390             }, true);
 391 
 392             Toolkit.getToolkit().removeTkListener(toolkitListener);
 393             toolkitListener = null;
 394             platformExitLatch.countDown();
 395         }
 396     }
 397 
 398     public static void exit() {
 399 //        System.err.println("PlatformImpl.exit");
 400         platformExit.set(true);
 401 
 402         // Notify listeners if any are registered, else exit directly
 403         if (listenersRegistered.get()) {
 404             notifyFinishListeners(true);
 405         } else {
 406 //            System.err.println("Platform.exit: calling doExit directly (no listeners)");
 407             tkExit();
 408         }
 409     }
 410 
 411     private static Boolean checkForClass(String classname) {
 412         try {
 413             Class.forName(classname, false, PlatformImpl.class.getClassLoader());
 414             return Boolean.TRUE;
 415         } catch (ClassNotFoundException cnfe) {
 416             return Boolean.FALSE;
 417         }
 418     }
 419 
 420     public static boolean isSupported(ConditionalFeature feature) {
 421         switch (feature) {
 422             case GRAPHICS:
 423                 if (isGraphicsSupported == null) {
 424                     isGraphicsSupported = checkForClass("javafx.stage.Stage");
 425                 }
 426                 return isGraphicsSupported;
 427             case CONTROLS:
 428                 if (isControlsSupported == null) {
 429                     isControlsSupported = checkForClass(
 430                             "javafx.scene.control.Control");
 431                 }
 432                 return isControlsSupported;
 433             case WEB:
 434                 if (isWebSupported == null) {
 435                     isWebSupported = checkForClass("javafx.scene.web.WebView");
 436                 }
 437                 return isWebSupported;
 438             case SWT:
 439                 if (isSWTSupported == null) {
 440                     isSWTSupported = checkForClass("javafx.embed.swt.FXCanvas");
 441                 }
 442                 return isSWTSupported;
 443             case SWING:
 444                 if (isSwingSupported == null) {
 445                     isSwingSupported = checkForClass(
 446                             "javafx.embed.swing.JFXPanel");
 447                 }
 448                 return isSwingSupported;
 449             case FXML:
 450                 if (isFXMLSupported == null) {
 451                     isFXMLSupported = checkForClass("javafx.fxml.FXMLLoader")
 452                             && checkForClass("javax.xml.stream.XMLInputFactory");
 453                 }
 454                 return isFXMLSupported;
 455             case TWO_LEVEL_FOCUS:
 456                 if (hasTwoLevelFocus == null) {
 457                     return Toolkit.getToolkit().isSupported(feature);
 458                 }
 459                 return hasTwoLevelFocus;
 460             case VIRTUAL_KEYBOARD:
 461                 if (hasVirtualKeyboard == null) {
 462                     return Toolkit.getToolkit().isSupported(feature);
 463                 }
 464                 return hasVirtualKeyboard;
 465             case INPUT_TOUCH:
 466                 if (hasTouch == null) {
 467                     return Toolkit.getToolkit().isSupported(feature);
 468                 }
 469                 return hasTouch;
 470             case INPUT_MULTITOUCH:
 471                 if (hasMultiTouch == null) {
 472                     return Toolkit.getToolkit().isSupported(feature);
 473                 }
 474                 return hasMultiTouch;
 475             case INPUT_POINTER:
 476                 if (hasPointer == null) {
 477                     return Toolkit.getToolkit().isSupported(feature);
 478                 }
 479                 return hasPointer;
 480             default:
 481                 return Toolkit.getToolkit().isSupported(feature);
 482         }
 483     }
 484 
 485     public static interface FinishListener {
 486         public void idle(boolean implicitExit);
 487         public void exitCalled();
 488     }
 489 
 490     /**
 491      * Set the platform user agent stylesheet to the default.
 492      */
 493     public static void setDefaultPlatformUserAgentStylesheet() {
 494         setPlatformUserAgentStylesheet(Application.STYLESHEET_MODENA);
 495     }
 496 
 497     private static boolean isModena = false;
 498     private static boolean isCaspian = false;
 499 
 500     /**
 501      * Current Platform User Agent Stylesheet is Modena.
 502      *
 503      * Note: Please think hard before using this as we really want to avoid special cases in the platform for specific
 504      * themes. This was added to allow tempory work arounds in the platform for bugs.
 505      *
 506      * @return true if using modena stylesheet
 507      */
 508     public static boolean isModena() {
 509         return isModena;
 510     }
 511 
 512     /**
 513      * Current Platform User Agent Stylesheet is Caspian.
 514      *
 515      * Note: Please think hard before using this as we really want to avoid special cases in the platform for specific
 516      * themes. This was added to allow tempory work arounds in the platform for bugs.
 517      *
 518      * @return true if using caspian stylesheet
 519      */
 520     public static boolean isCaspian() {
 521         return isCaspian;
 522     }
 523 
 524     /**
 525      * Set the platform user agent stylesheet to the given URL. This method has special handling for platform theme
 526      * name constants.
 527      */
 528     public static void setPlatformUserAgentStylesheet(String stylesheetUrl) {
 529         isModena = isCaspian = false;
 530         // check for command line override
 531         String overrideStylesheetUrl =
 532                 AccessController.doPrivileged(
 533                         new PrivilegedAction<String>() {
 534                             @Override public String run() {
 535                                 return System.getProperty("javafx.userAgentStylesheetUrl");
 536                             }
 537                         });
 538         if (overrideStylesheetUrl != null) stylesheetUrl = overrideStylesheetUrl;
 539         // check for named theme constants for modena and caspian
 540         if (Application.STYLESHEET_CASPIAN.equalsIgnoreCase(stylesheetUrl)) {
 541             isCaspian = true;
 542             AccessController.doPrivileged(
 543                     new PrivilegedAction() {
 544                         @Override public Object run() {
 545                             StyleManager.getInstance().setDefaultUserAgentStylesheet("com/sun/javafx/scene/control/skin/caspian/caspian.css");
 546 
 547                             if (isSupported(ConditionalFeature.INPUT_TOUCH)) {
 548                                 StyleManager.getInstance().addUserAgentStylesheet("com/sun/javafx/scene/control/skin/caspian/embedded.css");
 549 
 550                                 if (com.sun.javafx.Utils.isQVGAScreen()) {
 551                                     StyleManager.getInstance().addUserAgentStylesheet("com/sun/javafx/scene/control/skin/caspian/embedded-qvga.css");
 552                                 }
 553                             }
 554                             return null;
 555                         }
 556                     });
 557         } else if (Application.STYLESHEET_MODENA.equalsIgnoreCase(stylesheetUrl)) {
 558             isModena = true;
 559             AccessController.doPrivileged(
 560                     new PrivilegedAction() {
 561                         @Override public Object run() {
 562                             StyleManager.getInstance().setDefaultUserAgentStylesheet("com/sun/javafx/scene/control/skin/modena/modena.css");
 563                             if (isSupported(ConditionalFeature.INPUT_TOUCH)) {
 564                                 StyleManager.getInstance().addUserAgentStylesheet(
 565                                         "com/sun/javafx/scene/control/skin/modena/touch.css");
 566                             }
 567                             // when running on embedded add a extra stylesheet to tune performance of modena theme
 568                             if (PlatformUtil.isEmbedded()) {
 569                                 StyleManager.getInstance().addUserAgentStylesheet(
 570                                         "com/sun/javafx/scene/control/skin/modena/modena-embedded-performance.css");
 571                             }
 572                             return null;
 573                         }
 574                     });
 575         } else {
 576             StyleManager.getInstance().setDefaultUserAgentStylesheet(stylesheetUrl);
 577         }
 578     }
 579 }


