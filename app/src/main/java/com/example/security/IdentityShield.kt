package com.example.security

import com.example.data.model.DevicePlatform
import com.example.data.model.VirtualDeviceProfile
import com.example.data.model.VirtualModelCatalog
import com.example.data.model.VirtualModelPreset
import com.example.data.model.VirtualRegion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds the in-page identity layer used by every clone container.
 *
 * The container is a WebView, so these are the signals a web app can actually read:
 * user agent + client hints, navigator properties, screen metrics, timezone/locale,
 * WebGL, canvas fingerprint, WebRTC candidates and geolocation. Native telemetry such
 * as IMEI/serial is stored in the encrypted profile for native containers but is never
 * claimed to be readable by a website.
 */
object IdentityShield {

    private const val PRIVATE_IP_REGEX =
        "/(^|\\.)(10|127)\\.|(^|\\.)192\\.168\\.|(^|\\.)172\\.(1[6-9]|2[0-9]|3[01])\\./"

    fun buildShim(
        profile: VirtualDeviceProfile,
        region: VirtualRegion,
        desktopMode: Boolean = false,
        isWhatsAppIosMode: Boolean = false,
        strictAntiDetect: Boolean = true
    ): String {
        val isIos = profile.devicePlatform == DevicePlatform.IOS
        val langShort = region.localeTag.substringBefore("-")
        val navPlatform = when {
            desktopMode && isIos -> "MacIntel"
            desktopMode -> "Win32"
            else -> if (isIos) "iPhone" else "Linux armv8l"
        }
        val dpr = displayDpr(profile, isIos)
        val cssWidth = cssWidth(profile, desktopMode, isIos)
        val cssHeight = cssHeight(profile, desktopMode, isIos)
        val touchPoints = if (desktopMode) 0 else profile.maxTouchPoints.coerceAtLeast(5)
        val cores = profile.cpuCores.coerceIn(2, 16)
        // Chrome caps navigator.deviceMemory at 8 GB, reporting more is a detection signal.
        val memory = profile.deviceMemoryGb.coerceIn(2, 8)
        val chromeMajor = chromeMajor(profile.userAgent)
        val ua = jsString(if (desktopMode) profile.desktopUserAgent else profile.userAgent)
        val appVersion = jsString(
            (if (desktopMode) profile.desktopUserAgent else profile.userAgent)
                .removePrefix("Mozilla/")
        )
        val gmt = gmtLabel(region.timezoneOffsetMinutes)
        val zoneName = jsString(
            region.timezoneId.substringAfterLast('/').replace('_', ' ')
        )
        val chPlatform = when {
            isIos && desktopMode -> "macOS"
            isIos -> "iOS"
            desktopMode -> "Windows"
            else -> "Android"
        }
        val platformVersion = if (isIos) profile.androidVersion else apiPlatformVersion(profile.sdkInt)
        val vendor = jsString(if (isIos) "Apple Computer, Inc." else profile.navigatorVendor)
        val model = jsString(profile.model)
        val webglVendor = jsString(profile.webglVendor)
        val webglRenderer = jsString(profile.webglRenderer)
        val fpSeed = (profile.virtualAndroidId.hashCode() and 0xFF)

        val iosWaCssBlock = if (isWhatsAppIosMode) {
            """
            try {
              var styleId = 'clonix-wa-ios-theme';
              if (!document.getElementById(styleId) && document.head) {
                var st = document.createElement('style');
                st.id = styleId;
                st.textContent = [
                  'body, input, button, textarea { font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", Helvetica, Arial, sans-serif !important; }',
                  'header, [data-testid="chatlist-header"], [data-testid="conversation-header"] { backdrop-filter: blur(20px) !important; -webkit-backdrop-filter: blur(20px) !important; }',
                  '.message-out > div { border-radius: 18px 18px 4px 18px !important; }',
                  '.message-in > div { border-radius: 18px 18px 18px 4px !important; }'
                ].join('\n');
                document.head.appendChild(st);
              }
            } catch (e) {}
            """.trimIndent()
        } else ""

        val clientHintsBlock = if (isIos) {
            """
            try { delete Navigator.prototype.userAgentData; } catch (e) {}
            try { delete Navigator.prototype.connection; } catch (e) {}
            def(navigator, 'standalone', false);
            """.trimIndent()
        } else {
            """
            def(navigator, 'userAgentData', {
              brands: [
                { brand: 'Google Chrome', version: '$chromeMajor' },
                { brand: 'Chromium', version: '$chromeMajor' },
                { brand: 'Not?A_Brand', version: '24' }
              ],
              mobile: ${!desktopMode},
              platform: '$chPlatform',
              getHighEntropyValues: function () {
                return Promise.resolve({
                  architecture: 'arm',
                  bitness: '64',
                  brands: this.brands,
                  formFactors: ['${if (desktopMode) "Desktop" else "Mobile"}'],
                  fullVersionList: [
                    { brand: 'Google Chrome', version: '$chromeMajor.0.0.0' },
                    { brand: 'Chromium', version: '$chromeMajor.0.0.0' }
                  ],
                  mobile: ${!desktopMode},
                  model: '$model',
                  platform: '$chPlatform',
                  platformVersion: '$platformVersion',
                  uaFullVersion: '$chromeMajor.0.0.0',
                  wow64: false
                });
              }
            });
            try {
              if (navigator.connection) {
                def(navigator.connection, 'effectiveType', '4g');
                def(navigator.connection, 'rtt', 50);
                def(navigator.connection, 'downlink', 10);
                def(navigator.connection, 'saveData', false);
              }
            } catch (e) {}
            try {
              if (!window.chrome) {
                def(window, 'chrome', { app: { isInstalled: false }, runtime: {}, csi: function () {}, loadTimes: function () {} });
              }
            } catch (e) {}
            """.trimIndent()
        }

        val strictBlock = if (strictAntiDetect) {
            """
            try {
              var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
              CanvasRenderingContext2D.prototype.getImageData = function () {
                var image = origGetImageData.apply(this, arguments);
                try {
                  var d = image.data;
                  for (var i = 0; i < d.length; i += 4093) {
                    d[i] = (d[i] + 1) & 255;
                  }
                } catch (e) {}
                return image;
              };
            } catch (e) {}
            try {
              if (window.AnalyserNode && AnalyserNode.prototype.getFloatFrequencyData) {
                var origFreq = AnalyserNode.prototype.getFloatFrequencyData;
                AnalyserNode.prototype.getFloatFrequencyData = function (arr) {
                  origFreq.call(this, arr);
                  try {
                    for (var i = 0; i < arr.length; i += 61) {
                      arr[i] = arr[i] + 0.00001;
                    }
                  } catch (e) {}
                  return undefined;
                };
              }
            } catch (e) {}
            try {
              var NativeRtc = window.RTCPeerConnection;
              if (NativeRtc) {
                var sanitizeSdp = function (desc) {
                  if (desc && desc.sdp) {
                    desc.sdp = desc.sdp
                      .replace(/a=candidate:[^\r\n]*typ host[^\r\n]*/g, '')
                      .replace($PRIVATE_IP_REGEX, '127.0.0.1');
                  }
                  return desc;
                };
                var ClonixRtc = function (config, constraints) {
                  var pc = new NativeRtc(config, constraints);
                  var origOffer = pc.createOffer.bind(pc);
                  pc.createOffer = function () {
                    return Promise.resolve(origOffer.apply(pc, arguments)).then(sanitizeSdp);
                  };
                  var origAnswer = pc.createAnswer.bind(pc);
                  pc.createAnswer = function () {
                    return Promise.resolve(origAnswer.apply(pc, arguments)).then(sanitizeSdp);
                  };
                  var origSetLocal = pc.setLocalDescription.bind(pc);
                  pc.setLocalDescription = function (desc) {
                    if (desc && typeof desc === 'object') {
                      try { desc = sanitizeSdp(desc); } catch (e) {}
                    }
                    return origSetLocal(desc);
                  };
                  return pc;
                };
                ClonixRtc.prototype = NativeRtc.prototype;
                window.RTCPeerConnection = ClonixRtc;
                window.webkitRTCPeerConnection = ClonixRtc;
              }
            } catch (e) {}
            """.trimIndent()
        } else ""

        return """
            (function () {
              try {
                var def = function (obj, prop, val) {
                  try { Object.defineProperty(obj, prop, { get: function () { return val; }, configurable: true }); } catch (e) {}
                };
                var defMethod = function (obj, prop, fn) {
                  try { Object.defineProperty(obj, prop, { value: fn, configurable: true, writable: true }); } catch (e) {}
                };

                def(navigator, 'language', '${jsString(region.localeTag)}');
                def(navigator, 'languages', ['${jsString(region.localeTag)}', '${jsString(langShort)}']);
                def(navigator, 'platform', '$navPlatform');
                def(navigator, 'vendor', '$vendor');
                def(navigator, 'appVersion', '$appVersion');
                def(navigator, 'maxTouchPoints', $touchPoints);
                def(navigator, 'hardwareConcurrency', $cores);
                def(navigator, 'deviceMemory', $memory);
                def(navigator, 'webdriver', false);
                def(navigator, 'pdfViewerEnabled', false);
                def(navigator, 'doNotTrack', null);
                def(navigator, 'plugins', []);
                def(navigator, 'mimeTypes', []);

                $clientHintsBlock

                def(window, 'devicePixelRatio', $dpr);
                def(screen, 'width', $cssWidth);
                def(screen, 'height', $cssHeight);
                def(screen, 'availWidth', $cssWidth);
                def(screen, 'availHeight', ${cssHeight - 24});
                def(screen, 'colorDepth', 24);
                def(screen, 'pixelDepth', 24);
                try {
                  def(screen, 'orientation', {
                    type: '${if (cssHeight >= cssWidth) "portrait-primary" else "landscape-primary"}',
                    angle: 0,
                    addEventListener: function () {},
                    removeEventListener: function () {}
                  });
                } catch (e) {}

                try {
                  if (navigator.getBattery) {
                    defMethod(navigator, 'getBattery', function () {
                      return Promise.resolve({
                        charging: true,
                        chargingTime: 0,
                        dischargingTime: Infinity,
                        level: 0.87,
                        addEventListener: function () {},
                        removeEventListener: function () {}
                      });
                    });
                  }
                } catch (e) {}

                try {
                  if (window.Intl && Intl.DateTimeFormat) {
                    var origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
                    Intl.DateTimeFormat.prototype.resolvedOptions = function () {
                      var res = origResolved.apply(this, arguments);
                      res.timeZone = '${jsString(region.timezoneId)}';
                      res.locale = '${jsString(region.localeTag)}';
                      res.calendar = 'gregory';
                      res.numberingSystem = 'latn';
                      return res;
                    };
                    var origDateLocale = Date.prototype.toLocaleString;
                    Date.prototype.toLocaleString = function (locales, options) {
                      return origDateLocale.call(this, locales || '${jsString(region.localeTag)}', options || { timeZone: '${jsString(region.timezoneId)}' });
                    };
                    var origDateLocaleDate = Date.prototype.toLocaleDateString;
                    Date.prototype.toLocaleDateString = function (locales, options) {
                      return origDateLocaleDate.call(this, locales || '${jsString(region.localeTag)}', options || { timeZone: '${jsString(region.timezoneId)}' });
                    };
                    var origDateLocaleTime = Date.prototype.toLocaleTimeString;
                    Date.prototype.toLocaleTimeString = function (locales, options) {
                      return origDateLocaleTime.call(this, locales || '${jsString(region.localeTag)}', options || { timeZone: '${jsString(region.timezoneId)}' });
                    };
                    var origNumberLocale = Number.prototype.toLocaleString;
                    Number.prototype.toLocaleString = function (locales, options) {
                      return origNumberLocale.call(this, locales || '${jsString(region.localeTag)}', options);
                    };
                  }
                } catch (e) {}

                Date.prototype.getTimezoneOffset = function () { return ${region.timezoneOffsetMinutes}; };
                try {
                  var origDateToString = Date.prototype.toString;
                  Date.prototype.toString = function () {
                    try {
                      return origDateToString.call(this).replace(/GMT[+-]\d{4} \([^)]*\)/, '$gmt ($zoneName)');
                    } catch (e) {
                      return origDateToString.call(this);
                    }
                  };
                } catch (e) {}

                var patchWebGL = function (proto) {
                  if (!proto || !proto.getParameter) return;
                  var origGetParam = proto.getParameter;
                  proto.getParameter = function (param) {
                    if (param === 37445 || param === 0x9245) return '$webglVendor';
                    if (param === 37446 || param === 0x9246) return '$webglRenderer';
                    return origGetParam.apply(this, arguments);
                  };
                };
                if (window.WebGLRenderingContext) patchWebGL(WebGLRenderingContext.prototype);
                if (window.WebGL2RenderingContext) patchWebGL(WebGL2RenderingContext.prototype);

                $strictBlock

                try {
                  if (navigator.geolocation) {
                    var fakePos = {
                      coords: {
                        latitude: ${profile.latitude},
                        longitude: ${profile.longitude},
                        accuracy: 18,
                        altitude: null,
                        altitudeAccuracy: null,
                        heading: null,
                        speed: null
                      },
                      timestamp: Date.now()
                    };
                    navigator.geolocation.getCurrentPosition = function (ok) {
                      if (typeof ok === 'function') setTimeout(function () { ok(fakePos); }, 40);
                    };
                    navigator.geolocation.watchPosition = function (ok) {
                      if (typeof ok === 'function') setTimeout(function () { ok(fakePos); }, 40);
                      return 1;
                    };
                  }
                } catch (e) {}
                try {
                  if (navigator.permissions && navigator.permissions.query) {
                    var origPermissionQuery = navigator.permissions.query.bind(navigator.permissions);
                    navigator.permissions.query = function (desc) {
                      if (desc && desc.name === 'geolocation') {
                        return Promise.resolve({ state: 'granted', onchange: null });
                      }
                      return origPermissionQuery(desc);
                    };
                  }
                } catch (e) {}

                var fpSeed = $fpSeed;
                try { window.__clonixIdentitySeed = fpSeed; } catch (e) {}

                $iosWaCssBlock
              } catch (e) {}
            })();
        """.trimIndent()
    }

    /**
     * Reads back what the page actually sees so the UI can verify the override instead
     * of only claiming it. Returned as a JSON string.
     */
    fun probeScript(): String = """
        (function () {
          try {
            var gl = null;
            try {
              var c = document.createElement('canvas');
              gl = c.getContext('webgl') || c.getContext('experimental-webgl');
            } catch (e) {}
            var renderer = '';
            try {
              if (gl) renderer = gl.getParameter(0x9246) || gl.getParameter(37446) || '';
            } catch (e) {}
            return JSON.stringify({
              ua: String(navigator.userAgent || ''),
              platform: String(navigator.platform || ''),
              language: String(navigator.language || ''),
              languages: (navigator.languages || []).join(','),
              timezone: String(Intl.DateTimeFormat().resolvedOptions().timeZone || ''),
              offset: new Date().getTimezoneOffset(),
              screenWidth: screen.width,
              screenHeight: screen.height,
              dpr: window.devicePixelRatio,
              cores: navigator.hardwareConcurrency || 0,
              memory: navigator.deviceMemory || 0,
              touch: navigator.maxTouchPoints || 0,
              webgl: String(renderer),
              uaDataModel: (navigator.userAgentData && navigator.userAgentData.getHighEntropyValues ? 'present' : 'absent')
            });
          } catch (e) {
            return JSON.stringify({ error: String(e) });
          }
        })();
    """.trimIndent()

    /** Aligns the stored user agent with the device's real WebView engine version. */
    fun alignToRuntime(
        profile: VirtualDeviceProfile,
        webViewDefaultUserAgent: String
    ): VirtualDeviceProfile {
        if (profile.devicePlatform == DevicePlatform.IOS) return profile
        val major = chromeMajor(webViewDefaultUserAgent)
        val ua = "Mozilla/5.0 (Linux; Android ${profile.androidVersion}; ${profile.model}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
        val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        return profile.copy(userAgent = ua, desktopUserAgent = desktopUa)
    }

    fun displayDpr(profile: VirtualDeviceProfile, isIos: Boolean): Double {
        if (isIos) return 3.0
        val raw = profile.densityDpi / 160.0
        return (raw * 100).roundToInt() / 100.0
    }

    fun cssWidth(profile: VirtualDeviceProfile, desktopMode: Boolean, isIos: Boolean): Int {
        if (desktopMode) return 1440
        val dpr = displayDpr(profile, isIos)
        return (profile.screenWidth / dpr).roundToInt()
    }

    fun cssHeight(profile: VirtualDeviceProfile, desktopMode: Boolean, isIos: Boolean): Int {
        if (desktopMode) return 900
        val dpr = displayDpr(profile, isIos)
        return (profile.screenHeight / dpr).roundToInt()
    }

    private fun apiPlatformVersion(sdkInt: Int): String = when (sdkInt) {
        35 -> "15.0.0"
        34 -> "14.0.0"
        else -> "13.0.0"
    }

    private fun chromeMajor(userAgent: String): Int =
        Regex("Chrome/(\\d+)").find(userAgent)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 131

    private fun gmtLabel(offsetMinutes: Int): String {
        val sign = if (offsetMinutes <= 0) "+" else "-"
        val total = abs(offsetMinutes)
        return "GMT$sign%02d%02d".format(total / 60, total % 60)
    }

    private fun jsString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")

    @Suppress("unused")
    private fun presetFor(profile: VirtualDeviceProfile): VirtualModelPreset? =
        VirtualModelCatalog.findByModel(profile.model)
}
