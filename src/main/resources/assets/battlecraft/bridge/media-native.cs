using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using Windows.Foundation;
using Windows.Media.Control;
using Windows.Storage.Streams;

public static class BattleCraftMedia {
    const int AwaitSteps = 400;
    const int AwaitBriefSteps = 60;
    const int AwaitSleepMs = 5;
    const float BlindGate = 0.0015f;
    const int BlindArm = 3;

    static readonly string[] Sites = {
        "soundcloud", "youtube music", "youtube", "spotify", "music.yandex",
        "vk.com", "vkmusic", "twitch", "deezer", "bandcamp", "apple music", "zvuk"
    };

    static readonly ProcessMeter listener = new ProcessMeter(4);
    static readonly PlayerScan scan = new PlayerScan();

    static GlobalSystemMediaTransportControlsSessionManager manager;
    static string signature = "";
    static string lastApp = "";
    static string lastSite = "";
    static int armed;
    static long artStamp;

    public static void Init() {
        manager = Await(GlobalSystemMediaTransportControlsSessionManager.RequestAsync());
    }

    static T Await<T>(IAsyncOperation<T> operation) {
        return Await(operation, AwaitSteps);
    }

    static T Await<T>(IAsyncOperation<T> operation, int steps) {
        int guard = 0;
        while (operation.Status == AsyncStatus.Started && guard++ < steps) Thread.Sleep(AwaitSleepMs);
        if (operation.Status != AsyncStatus.Completed) throw new InvalidOperationException("async " + operation.Status);
        return operation.GetResults();
    }

    // WHY: сессия без названия и сессия, у которой падает опрос свойств, это остаток убитой вкладки.
    // WHY: браузер держит его в списке, а SoundCloud не двигает LastUpdatedTime между треками, поэтому
    // WHY: по одной свежести такой остаток перебивает живую сессию и остров пустеет
    static Chosen Pick() {
        var sessions = manager.GetSessions();
        if (sessions == null || sessions.Count == 0) return null;

        Chosen spare = null;
        foreach (var session in sessions.OrderByDescending(Live).ThenByDescending(Seen)) {
            var properties = Describe(session);
            if (properties == null) continue;
            if (!string.IsNullOrEmpty(properties.Title)) return new Chosen(session, properties);
            if (spare == null) spare = new Chosen(session, properties);
        }
        return spare;
    }

    static int Live(GlobalSystemMediaTransportControlsSession session) {
        try {
            return session.GetPlaybackInfo().PlaybackStatus
                == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing ? 1 : 0;
        } catch {
            return 0;
        }
    }

    static DateTimeOffset Seen(GlobalSystemMediaTransportControlsSession session) {
        try {
            return session.GetTimelineProperties().LastUpdatedTime;
        } catch {
            return DateTimeOffset.MinValue;
        }
    }

    static GlobalSystemMediaTransportControlsSessionMediaProperties Describe(
            GlobalSystemMediaTransportControlsSession session) {
        try {
            return Await(session.TryGetMediaPropertiesAsync(), AwaitBriefSteps);
        } catch {
            return null;
        }
    }

    public static string Poll(string artPath) {
        var picked = Pick();
        if (picked == null) return Blind();

        string app = picked.Session.SourceAppUserModelId ?? "";
        lastApp = app;
        lastSite = SiteOf(app);
        armed = BlindArm;
        Restamp(app, picked.Properties, artPath);
        return Serialize(picked.Session, picked.Properties, app, lastSite);
    }

    // WHY: Firefox после перезагрузки вкладки перестаёт публиковать сессию SMTC, хотя звук идёт, и
    // WHY: поднимает её обратно только на новом медиаэлементе (реклама, быстрая смена трека). Название
    // WHY: брать неоткуда, поэтому остров держится на звуке самого приложения и показывает площадку
    static string Blind() {
        signature = "";
        string app = Sounding();
        if (app.Length == 0) return "{\"ok\":false}";

        var json = new StringBuilder("{\"ok\":true,\"blind\":true");
        Put(json, "app", app);
        Put(json, "site", lastSite);
        Put(json, "title", "");
        Put(json, "artist", "");
        Put(json, "album", "");
        Put(json, "status", "Playing");
        json.Append(",\"playing\":true,\"pos\":0,\"dur\":0,\"age\":0,\"art\":").Append(artStamp).Append('}');
        return json.ToString();
    }

    static string Sounding() {
        string app = Candidate();
        if (app.Length == 0) {
            armed = Math.Max(0, armed - 1);
            return "";
        }
        if (armed < BlindArm) {
            armed++;
            return "";
        }
        return app;
    }

    static string Candidate() {
        if (lastApp.Length > 0) {
            float peak = listener.Peak(lastApp);
            if (listener.Bound()) return peak > BlindGate ? lastApp : "";
            lastApp = "";
            lastSite = "";
        }

        string found = scan.Loudest(BlindGate);
        if (found.Length == 0) return "";

        lastApp = found;
        lastSite = SiteOf(found);
        return found;
    }

    static void Restamp(string app, GlobalSystemMediaTransportControlsSessionMediaProperties properties, string artPath) {
        string stamp = app + "|" + (properties.Title ?? "") + "|" + (properties.Artist ?? "");
        if (stamp == signature) return;

        signature = stamp;
        if (SaveArt(properties, artPath)) artStamp++;
    }

    static string Serialize(GlobalSystemMediaTransportControlsSession session,
                            GlobalSystemMediaTransportControlsSessionMediaProperties properties,
                            string app, string site) {
        var timeline = session.GetTimelineProperties();
        var status = session.GetPlaybackInfo().PlaybackStatus;
        bool playing = status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
        long age = (long)Math.Max(0.0, (DateTimeOffset.Now - timeline.LastUpdatedTime).TotalMilliseconds);

        var json = new StringBuilder("{\"ok\":true");
        Put(json, "app", app);
        Put(json, "site", site);
        Put(json, "title", properties.Title ?? "");
        Put(json, "artist", properties.Artist ?? "");
        Put(json, "album", properties.AlbumTitle ?? "");
        Put(json, "status", status.ToString());
        json.Append(",\"playing\":").Append(playing ? "true" : "false");
        json.Append(",\"pos\":").Append((long)timeline.Position.TotalMilliseconds);
        json.Append(",\"dur\":").Append((long)timeline.EndTime.TotalMilliseconds);
        json.Append(",\"age\":").Append(age);
        json.Append(",\"art\":").Append(artStamp);
        json.Append('}');
        return json.ToString();
    }

    static bool SaveArt(GlobalSystemMediaTransportControlsSessionMediaProperties properties, string artPath) {
        if (properties.Thumbnail == null || string.IsNullOrEmpty(artPath)) return false;

        try {
            byte[] bytes = ReadThumbnail(properties);
            if (bytes.Length == 0) return false;

            string staging = artPath + ".part";
            File.WriteAllBytes(staging, bytes);
            if (File.Exists(artPath)) File.Delete(artPath);
            File.Move(staging, artPath);
            return true;
        } catch {
            return false;
        }
    }

    static byte[] ReadThumbnail(GlobalSystemMediaTransportControlsSessionMediaProperties properties) {
        var stream = Await(properties.Thumbnail.OpenReadAsync());
        uint size = (uint)stream.Size;
        if (size == 0 || size > 8 * 1024 * 1024) {
            stream.Dispose();
            return new byte[0];
        }

        var reader = new DataReader(stream.GetInputStreamAt(0));
        Await(reader.LoadAsync(size));
        byte[] bytes = new byte[size];
        reader.ReadBytes(bytes);
        reader.Dispose();
        stream.Dispose();
        return bytes;
    }

    static void Put(StringBuilder json, string key, string value) {
        json.Append(",\"").Append(key).Append("\":\"").Append(Escape(value)).Append('"');
    }

    static string Escape(string value) {
        var text = new StringBuilder(value.Length + 8);
        foreach (char symbol in value) {
            if (symbol == '"' || symbol == '\\') text.Append('\\').Append(symbol);
            else if (symbol < ' ') text.Append(' ');
            else text.Append(symbol);
        }
        return text.ToString();
    }

    static string SiteOf(string app) {
        if (!app.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) return "";

        string process = Path.GetFileNameWithoutExtension(app);
        var owners = new HashSet<uint>();
        foreach (var running in SafeProcesses(process)) owners.Add((uint)running.Id);
        if (owners.Count == 0) return "";

        foreach (string caption in Titles(owners)) {
            string lower = caption.ToLowerInvariant();
            foreach (string site in Sites) {
                if (lower.Contains(site)) return site;
            }
        }
        return "";
    }

    static Process[] SafeProcesses(string name) {
        try {
            return Process.GetProcessesByName(name);
        } catch {
            return new Process[0];
        }
    }

    delegate bool EnumProc(IntPtr window, IntPtr parameter);

    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, IntPtr parameter);
    [DllImport("user32.dll")] static extern int GetWindowTextLength(IntPtr window);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr window, StringBuilder text, int limit);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint owner);

    static List<string> Titles(HashSet<uint> owners) {
        var found = new List<string>();
        EnumWindows((window, parameter) => {
            uint owner;
            GetWindowThreadProcessId(window, out owner);
            if (!owners.Contains(owner)) return true;

            int length = GetWindowTextLength(window);
            if (length == 0) return true;

            var caption = new StringBuilder(length + 1);
            GetWindowText(window, caption, caption.Capacity);
            found.Add(caption.ToString());
            return true;
        }, IntPtr.Zero);
        return found;
    }
}

class Chosen {
    public readonly GlobalSystemMediaTransportControlsSession Session;
    public readonly GlobalSystemMediaTransportControlsSessionMediaProperties Properties;

    public Chosen(GlobalSystemMediaTransportControlsSession session,
                  GlobalSystemMediaTransportControlsSessionMediaProperties properties) {
        Session = session;
        Properties = properties;
    }
}

class MeterEntry {
    public string App;
    public IAudioMeterInformation Meter;
}

static class AudioTaps {
    // WHY: менеджер держится в поле, пока живут снятые с него метры сессий
    static IAudioSessionManager2 anchor;

    public static IMMDevice Endpoint() {
        var enumerator = (IMMDeviceEnumerator) new MMDeviceEnumerator();
        IMMDevice device;
        if (enumerator.GetDefaultAudioEndpoint(0, 0, out device) != 0) return null;
        return device;
    }

    public static IAudioMeterInformation Whole(IMMDevice device) {
        Guid iid = typeof(IAudioMeterInformation).GUID;
        object created;
        if (device == null || device.Activate(ref iid, 23, IntPtr.Zero, out created) != 0) return null;
        return created as IAudioMeterInformation;
    }

    public static List<MeterEntry> Each(IMMDevice device) {
        var found = new List<MeterEntry>();
        Guid iid = typeof(IAudioSessionManager2).GUID;
        object created;
        if (device == null || device.Activate(ref iid, 23, IntPtr.Zero, out created) != 0) return found;

        anchor = created as IAudioSessionManager2;
        IAudioSessionEnumerator list;
        if (anchor == null || anchor.GetSessionEnumerator(out list) != 0 || list == null) return found;

        int count;
        if (list.GetCount(out count) != 0) return found;
        for (int index = 0; index < count; index++) Collect(found, list, index);
        return found;
    }

    static void Collect(List<MeterEntry> found, IAudioSessionEnumerator list, int index) {
        IntPtr pointer;
        if (list.GetSession(index, out pointer) != 0 || pointer == IntPtr.Zero) return;

        object session = Marshal.GetObjectForIUnknown(pointer);
        Marshal.Release(pointer);
        var control = session as IAudioSessionControl2;
        var meter = session as IAudioMeterInformation;
        if (control == null || meter == null) return;

        uint owner;
        if (control.GetProcessId(out owner) != 0 || owner == 0) return;

        string name = NameOf(owner);
        if (name.Length == 0) return;

        var entry = new MeterEntry();
        entry.App = name;
        entry.Meter = meter;
        found.Add(entry);
    }

    public static string NameOf(uint owner) {
        try {
            return Process.GetProcessById((int) owner).ProcessName.ToLowerInvariant();
        } catch {
            return "";
        }
    }

    public static string ProcessName(string app) {
        string trimmed = (app ?? "").Trim().ToLowerInvariant();
        if (!trimmed.EndsWith(".exe")) return "";
        return trimmed.Substring(0, trimmed.Length - 4);
    }
}

// WHY: метр отдаёт пик, накопленный с прошлого чтения, поэтому свежесозданный объект первым
// WHY: чтением всегда возвращает почти ноль. Пересобирать его по таймеру нельзя: раз в секунду
// WHY: визуализатор проваливался в пол на ровном звуке. Пересборка только пока сидим на
// WHY: запасном метре устройства, и первое чтение после неё отдаётся прошлым значением
class ProcessMeter {
    readonly int retries;

    IAudioMeterInformation meter;
    string mounted = "";
    bool bound;
    int calls;
    float held;

    public ProcessMeter(int retries) {
        this.retries = retries;
    }

    public bool Bound() {
        return bound;
    }

    public float Peak(string app) {
        try {
            string wanted = app ?? "";
            if (meter == null || mounted != wanted || (!bound && ++calls >= retries)) {
                Mount(wanted);
                return held;
            }

            float peak;
            if (meter.GetPeakValue(out peak) != 0) {
                meter = null;
                return held;
            }
            held = peak;
            return peak;
        } catch {
            meter = null;
            bound = false;
            return held;
        }
    }

    void Mount(string app) {
        calls = 0;
        if (mounted != app) held = 0.0f;
        mounted = app;
        meter = null;
        bound = false;

        IMMDevice device = AudioTaps.Endpoint();
        if (device == null) return;

        meter = Named(device, app);
        bound = meter != null;
        if (meter == null) meter = AudioTaps.Whole(device);
    }

    static IAudioMeterInformation Named(IMMDevice device, string app) {
        string wanted = AudioTaps.ProcessName(app);
        if (wanted.Length == 0) return null;

        foreach (var entry in AudioTaps.Each(device)) {
            if (entry.App == wanted) return entry.Meter;
        }
        return null;
    }
}

// WHY: когда сессии SMTC нет вообще, играющее приложение искать больше негде: метры всех сессий
// WHY: известных плееров держатся живыми между опросами, потому что свежий метр читается нулём
class PlayerScan {
    const int Rebuild = 8;

    static readonly string[] Known = {
        "firefox", "librewolf", "waterfox", "zen", "chrome", "chromium", "msedge", "brave",
        "vivaldi", "opera", "browser", "tor", "spotify", "vlc", "aimp", "foobar2000"
    };

    readonly List<MeterEntry> taps = new List<MeterEntry>();
    int age;

    public string Loudest(float gate) {
        try {
            if (age <= 0) Rescan();
            age--;
            return Best(gate);
        } catch {
            taps.Clear();
            age = 0;
            return "";
        }
    }

    string Best(float gate) {
        string best = "";
        float top = gate;
        foreach (var tap in taps) {
            float peak;
            if (tap.Meter.GetPeakValue(out peak) != 0 || peak <= top) continue;
            top = peak;
            best = tap.App;
        }
        return best.Length == 0 ? "" : best + ".exe";
    }

    void Rescan() {
        age = Rebuild;
        taps.Clear();
        foreach (var entry in AudioTaps.Each(AudioTaps.Endpoint())) {
            if (Array.IndexOf(Known, entry.App) >= 0) taps.Add(entry);
        }
    }
}

// WHY: полоски эквалайзера ведёт настоящий уровень звука, а не синусоида. Замер берётся у сессии
// WHY: того приложения, что играет по SMTC, и только если её не найти, у устройства целиком:
// WHY: иначе в полоски били бы щелчки самой игры
public static class BattleCraftLevel {
    const int RetryCalls = 60;

    static readonly ProcessMeter probe = new ProcessMeter(RetryCalls);

    public static string Poll(string app) {
        return "{\"peak\":" + probe.Peak(app).ToString("0.0000", CultureInfo.InvariantCulture) + "}";
    }
}

[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
class MMDeviceEnumerator { }

[ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMMDeviceEnumerator {
    [PreserveSig] int EnumAudioEndpoints(int flow, int state, out IntPtr devices);
    [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IMMDevice device);
}

[ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMMDevice {
    [PreserveSig] int Activate(ref Guid iid, int context, IntPtr parameters, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
}

[ComImport, Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioMeterInformation {
    [PreserveSig] int GetPeakValue(out float peak);
}

[ComImport, Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioSessionManager2 {
    [PreserveSig] int GetAudioSessionControl(IntPtr group, int flags, out IntPtr control);
    [PreserveSig] int GetSimpleAudioVolume(IntPtr group, int cross, out IntPtr volume);
    [PreserveSig] int GetSessionEnumerator(out IAudioSessionEnumerator sessions);
}

[ComImport, Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioSessionEnumerator {
    [PreserveSig] int GetCount(out int count);
    [PreserveSig] int GetSession(int index, out IntPtr session);
}

[ComImport, Guid("bfb7ff88-7239-4fc9-8fa2-07c950be9c6d"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioSessionControl2 {
    [PreserveSig] int GetState(out int state);
    [PreserveSig] int GetDisplayName([MarshalAs(UnmanagedType.LPWStr)] out string name);
    [PreserveSig] int SetDisplayName([MarshalAs(UnmanagedType.LPWStr)] string name, ref Guid context);
    [PreserveSig] int GetIconPath([MarshalAs(UnmanagedType.LPWStr)] out string path);
    [PreserveSig] int SetIconPath([MarshalAs(UnmanagedType.LPWStr)] string path, ref Guid context);
    [PreserveSig] int GetGroupingParam(out Guid group);
    [PreserveSig] int SetGroupingParam(ref Guid group, ref Guid context);
    [PreserveSig] int RegisterAudioSessionNotification(IntPtr notify);
    [PreserveSig] int UnregisterAudioSessionNotification(IntPtr notify);
    [PreserveSig] int GetSessionIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
    [PreserveSig] int GetSessionInstanceIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
    [PreserveSig] int GetProcessId(out uint owner);
    [PreserveSig] int IsSystemSoundsSession();
    [PreserveSig] int SetDuckingPreference(bool optOut);
}

// WHY: усиления идут лесенкой вверх по частоте: у музыки энергия падает примерно на три децибела
// WHY: на октаву, и без этого наклона бас с нижней серединой стоят в потолке, а верх не видно.
// WHY: пик за интервал у сведённой музыки почти не гуляет, поэтому полоски по нему стоят на месте.
// WHY: Настоящий визуализатор берёт сам поток: loopback-захват вывода, окно Ханна, БПФ и пять полос
// WHY: по логарифму частоты. Тогда бас, голос и тарелки живут отдельно и ничего не подстраивается
public static class BattleCraftSpectrum {
    const int Size = 1024;
    const int Bands = 6;
    const int LoopbackFlag = 0x00020000;
    const int EventFlag = 0x00040000;
    const int StopWaitMs = 200;
    const long BufferSpan = 2000000;
    static readonly int[] Edge = {1, 3, 7, 15, 32, 75, 200};
    static readonly float[] Gain = {1.0f, 1.6f, 2.4f, 3.4f, 5.0f, 8.2f};

    static readonly object door = new object();
    static readonly float[] ring = new float[Size];
    static readonly float[] window = new float[Size];
    static readonly float[] level = new float[Bands];

    static IAudioClient client;
    static IAudioCaptureClient capture;
    static Thread worker;
    static IntPtr signal;
    static string aimed = "";
    static volatile bool running;
    static volatile bool broken;
    static volatile string fault = "";
    static int cursor;
    static int channels;
    static bool floating;
    static bool whole;
    static int tag;
    static int bits;

    public static bool Ready() {
        return running && !broken;
    }

    public static string Fault() {
        return fault;
    }

    // WHY: захват идёт по дереву процессов играющего приложения, а не «всё кроме игры»: иначе в
    // WHY: полоски бьют чужие звуки, например голоса в дискорде. Приложение неизвестно - остаётся
    // WHY: прежний захват с исключением самой игры
    public static string Poll(int excluded, string app) {
        string wanted = app ?? "";
        if (running && wanted.Length > 0 && wanted != aimed) Restart(excluded, wanted);
        if (!running && !broken) Start(excluded, wanted);
        if (!Ready()) return null;

        var text = new StringBuilder("{\"b\":[");
        lock (door) {
            for (int band = 0; band < Bands; band++) {
                if (band > 0) text.Append(",");
                text.Append(level[band].ToString("0.0000", CultureInfo.InvariantCulture));
            }
        }
        return text.Append("]}").ToString();
    }

    static void Restart(int excluded, string app) {
        Halt();
        broken = false;
        fault = "";
        Start(excluded, app);
    }

    // WHY: без явного освобождения обоих объектов вторая наводка на то же приложение падает на
    // WHY: Initialize: старый поток процессного loopback ещё держит виртуальное устройство
    static void Halt() {
        running = false;
        Thread stopping = worker;
        worker = null;
        if (stopping != null) stopping.Join(StopWaitMs);

        try {
            if (client != null) client.Stop();
        } catch {
            fault = "stop";
        }
        Release(capture);
        Release(client);
        capture = null;
        client = null;
        if (signal == IntPtr.Zero) return;

        CloseHandle(signal);
        signal = IntPtr.Zero;
    }

    static void Release(object instance) {
        try {
            if (instance != null && Marshal.IsComObject(instance)) Marshal.FinalReleaseComObject(instance);
        } catch {
            fault = "release";
        }
    }

    static void Start(int excluded, string app) {
        try {
            for (int i = 0; i < Size; i++) {
                window[i] = 0.5f - 0.5f * (float) Math.Cos(2.0 * Math.PI * i / (Size - 1));
            }
            aimed = app;
            Open(excluded, app);
            running = true;
            worker = new Thread(Pump);
            worker.IsBackground = true;
            worker.Start();
        } catch (Exception error) {
            fault = error.GetType().Name + ": " + error.Message;
            broken = true;
            running = false;
        }
    }

    static void Open(int excluded, string app) {
        int player = ProcessTree.RootOf(app);
        client = player > 0 ? Alone(player, ProcessLoopback.IncludeTargetTree) : null;
        if (client == null && excluded > 0) client = Alone(excluded, ProcessLoopback.ExcludeTargetTree);
        if (client != null) {
            whole = false;
            Attach(Shape());
            return;
        }
        whole = true;
        OpenDevice();
    }

    // WHY: у виртуального устройства процессного loopback нет своего формата, его задаём сами
    static IntPtr Shape() {
        IntPtr shape = Marshal.AllocHGlobal(18);
        Marshal.WriteInt16(shape, 0, 3);
        Marshal.WriteInt16(shape, 2, 2);
        Marshal.WriteInt32(shape, 4, 48000);
        Marshal.WriteInt32(shape, 8, 48000 * 8);
        Marshal.WriteInt16(shape, 12, 8);
        Marshal.WriteInt16(shape, 14, 32);
        Marshal.WriteInt16(shape, 16, 0);
        channels = 2;
        floating = true;
        return shape;
    }

    static IAudioClient Alone(int target, int mode) {
        try {
            return (IAudioClient) ProcessLoopback.Open(target, mode);
        } catch (Exception error) {
            fault = "process: " + error.Message;
            return null;
        }
    }

    static void Attach(IntPtr shape) {
        try {
            int hr = client.Initialize(0, LoopbackFlag | EventFlag, BufferSpan, 0, shape, IntPtr.Zero);
            if (hr != 0) throw new Exception("init 0x" + hr.ToString("X8"));
        } finally {
            Marshal.FreeHGlobal(shape);
        }
        signal = CreateEventW(IntPtr.Zero, false, false, null);
        client.SetEventHandle(signal);
        Grab();
    }

    static void Grab() {
        Guid captureId = new Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317");
        IntPtr raw;
        int hr = client.GetService(ref captureId, out raw);
        if (hr != 0 || raw == IntPtr.Zero) throw new Exception("capture 0x" + hr.ToString("X8"));

        capture = (IAudioCaptureClient) Marshal.GetObjectForIUnknown(raw);
        Marshal.Release(raw);
        if (client.Start() != 0) throw new Exception("start");
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    static extern IntPtr CreateEventW(IntPtr attributes, bool manual, bool signalled, string name);

    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr handle);

    static void OpenDevice() {
        var enumerator = (IMMDeviceEnumerator) new MMDeviceEnumerator();
        IMMDevice device;
        if (enumerator.GetDefaultAudioEndpoint(0, 0, out device) != 0 || device == null) throw new Exception("endpoint");

        Guid iid = typeof(IAudioClient).GUID;
        object created;
        if (device.Activate(ref iid, 23, IntPtr.Zero, out created) != 0) throw new Exception("activate");

        client = (IAudioClient) created;
        IntPtr shape;
        if (client.GetMixFormat(out shape) != 0) throw new Exception("format");

        var format = (WaveFormat) Marshal.PtrToStructure(shape, typeof(WaveFormat));
        channels = format.channels;
        tag = format.tag;
        bits = format.bits;
        floating = format.tag == 3 || (format.tag == -2 && format.bits == 32);
        if (client.Initialize(0, LoopbackFlag, BufferSpan, 0, shape, IntPtr.Zero) != 0) throw new Exception("init");

        Grab();
    }

    static void Pump() {
        try {
            while (running) {
                Drain();
                Thread.Sleep(8);
            }
        } catch {
            broken = true;
            running = false;
        }
    }

    static void Drain() {
        int frames;
        while (capture.GetNextPacketSize(out frames) == 0 && frames > 0) {
            IntPtr data;
            int got;
            int flags;
            long position;
            long stamp;
            if (capture.GetBuffer(out data, out got, out flags, out position, out stamp) != 0) return;

            if ((flags & 2) != 0) Silence(got); else Absorb(data, got);
            capture.ReleaseBuffer(got);
        }
        Transform();
    }

    static void Silence(int frames) {
        for (int i = 0; i < frames; i++) {
            ring[cursor] = 0.0f;
            cursor = (cursor + 1) % Size;
        }
    }

    static void Absorb(IntPtr data, int frames) {
        float gain = whole ? 1.0f / BattleCraftVolume.Divider() : 1.0f;
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0.0f;
            for (int channel = 0; channel < channels; channel++) {
                int at = frame * channels + channel;
                sum += floating ? ReadFloat(data, at) : Marshal.ReadInt16(data, at * 2) / 32768.0f;
            }
            ring[cursor] = sum / channels * gain;
            cursor = (cursor + 1) % Size;
        }
    }

    static float ReadFloat(IntPtr data, int index) {
        return BitConverter.ToSingle(BitConverter.GetBytes(Marshal.ReadInt32(data, index * 4)), 0);
    }

    static void Transform() {
        var real = new float[Size];
        var imaginary = new float[Size];
        lock (door) {
            for (int i = 0; i < Size; i++) {
                real[i] = ring[(cursor + i) % Size] * window[i];
            }
        }
        Fourier(real, imaginary);
        Measure(real, imaginary);
    }

    static void Measure(float[] real, float[] imaginary) {
        var fresh = new float[Bands];
        for (int band = 0; band < Bands; band++) {
            float sum = 0.0f;
            int from = Edge[band];
            int to = Math.Min(Edge[band + 1], Size / 2);
            for (int bin = from; bin < to; bin++) {
                sum += (float) Math.Sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]);
            }
            float loud = sum / Math.Max(1, to - from) * Gain[band];
            fresh[band] = (float) Math.Sqrt(loud);
        }
        lock (door) {
            Array.Copy(fresh, level, Bands);
        }
    }

    static void Fourier(float[] real, float[] imaginary) {
        Reorder(real, imaginary);
        for (int len = 2; len <= Size; len <<= 1) {
            Butterflies(real, imaginary, len);
        }
    }

    static void Reorder(float[] real, float[] imaginary) {
        for (int i = 1, j = 0; i < Size; i++) {
            int bit = Size >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i >= j) continue;

            float swap = real[i]; real[i] = real[j]; real[j] = swap;
            swap = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = swap;
        }
    }

    static void Butterflies(float[] real, float[] imaginary, int len) {
        double angle = -2.0 * Math.PI / len;
        float stepReal = (float) Math.Cos(angle);
        float stepImaginary = (float) Math.Sin(angle);
        for (int start = 0; start < Size; start += len) {
            float turnReal = 1.0f;
            float turnImaginary = 0.0f;
            for (int at = 0; at < len / 2; at++) {
                int here = start + at;
                int there = here + len / 2;
                float partReal = real[there] * turnReal - imaginary[there] * turnImaginary;
                float partImaginary = real[there] * turnImaginary + imaginary[there] * turnReal;
                real[there] = real[here] - partReal;
                imaginary[there] = imaginary[here] - partImaginary;
                real[here] += partReal;
                imaginary[here] += partImaginary;
                float carried = turnReal * stepReal - turnImaginary * stepImaginary;
                turnImaginary = turnReal * stepImaginary + turnImaginary * stepReal;
                turnReal = carried;
            }
        }
    }
}

[StructLayout(LayoutKind.Sequential, Pack = 1)]
struct WaveFormat {
    public short tag;
    public short channels;
    public int rate;
    public int bytesPerSecond;
    public short align;
    public short bits;
    public short extra;
}

[ComImport, Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioClient {
    [PreserveSig] int Initialize(int mode, int flags, long duration, long period, IntPtr format, IntPtr session);
    [PreserveSig] int GetBufferSize(out int frames);
    [PreserveSig] int GetStreamLatency(out long latency);
    [PreserveSig] int GetCurrentPadding(out int frames);
    [PreserveSig] int IsFormatSupported(int mode, IntPtr format, IntPtr closest);
    [PreserveSig] int GetMixFormat(out IntPtr format);
    [PreserveSig] int GetDevicePeriod(out long process, out long minimum);
    [PreserveSig] int Start();
    [PreserveSig] int Stop();
    [PreserveSig] int Reset();
    [PreserveSig] int SetEventHandle(IntPtr handle);
    [PreserveSig] int GetService(ref Guid iid, out IntPtr instance);
}

[ComImport, Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioCaptureClient {
    [PreserveSig] int GetBuffer(out IntPtr data, out int frames, out int flags, out long position, out long stamp);
    [PreserveSig] int ReleaseBuffer(int frames);
    [PreserveSig] int GetNextPacketSize(out int frames);
}

// WHY: браузер это дерево процессов, звук идёт из дочернего, а захват просят по корню дерева.
// WHY: корень это тот процесс с нужным именем, чей родитель называется иначе
static class ProcessTree {
    const int SnapProcess = 2;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    struct Entry {
        public int size;
        public int usage;
        public int pid;
        public IntPtr heap;
        public int module;
        public int threads;
        public int parent;
        public int priority;
        public int flags;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)] public string name;
    }

    [DllImport("kernel32.dll")] static extern IntPtr CreateToolhelp32Snapshot(int flags, int pid);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] static extern bool Process32FirstW(IntPtr snapshot, ref Entry entry);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] static extern bool Process32NextW(IntPtr snapshot, ref Entry entry);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr snapshot);

    public static int RootOf(string app) {
        string wanted = AudioTaps.ProcessName(app);
        if (wanted.Length == 0) return 0;

        var parents = new Dictionary<int, int>();
        var named = new List<int>();
        try {
            Walk(wanted, parents, named);
        } catch {
            return 0;
        }
        return Eldest(parents, named);
    }

    static void Walk(string wanted, Dictionary<int, int> parents, List<int> named) {
        IntPtr snapshot = CreateToolhelp32Snapshot(SnapProcess, 0);
        if (snapshot == IntPtr.Zero || snapshot == new IntPtr(-1)) return;

        try {
            var entry = new Entry();
            entry.size = Marshal.SizeOf(typeof(Entry));
            bool more = Process32FirstW(snapshot, ref entry);
            while (more) {
                parents[entry.pid] = entry.parent;
                if (Bare(entry.name) == wanted) named.Add(entry.pid);
                more = Process32NextW(snapshot, ref entry);
            }
        } finally {
            CloseHandle(snapshot);
        }
    }

    static int Eldest(Dictionary<int, int> parents, List<int> named) {
        foreach (int pid in named) {
            int parent;
            if (!parents.TryGetValue(pid, out parent) || !named.Contains(parent)) return pid;
        }
        return named.Count > 0 ? named[0] : 0;
    }

    static string Bare(string name) {
        string trimmed = (name ?? "").Trim().ToLowerInvariant();
        return trimmed.EndsWith(".exe") ? trimmed.Substring(0, trimmed.Length - 4) : trimmed;
    }
}

// WHY: обычный loopback снимает весь вывод карты, поэтому в полоски бьют и звуки самой игры.
// WHY: Процессный loopback (Windows 10 2004 и новее) умеет исключить дерево процессов: просим
// WHY: всё, кроме самого майнкрафта. Если не выйдет, остаётся прежний захват устройства целиком
public static class ProcessLoopback {
    const string VirtualDevice = "VAD\\Process_Loopback";
    const int ActivationProcessLoopback = 1;
    public const int IncludeTargetTree = 0;
    public const int ExcludeTargetTree = 1;
    const int BlobType = 65;
    const int WaitMs = 2000;

    [DllImport("Mmdevapi.dll", CharSet = CharSet.Unicode, ExactSpelling = true, PreserveSig = false)]
    static extern void ActivateAudioInterfaceAsync(string path, ref Guid iid, IntPtr parameters,
            IActivateAudioInterfaceCompletionHandler handler, out IActivateAudioInterfaceAsyncOperation operation);

    public static object Open(int target, int mode) {
        IntPtr blob = Marshal.AllocHGlobal(12);
        IntPtr variant = Marshal.AllocHGlobal(24);
        try {
            Marshal.WriteInt32(blob, 0, ActivationProcessLoopback);
            Marshal.WriteInt32(blob, 4, target);
            Marshal.WriteInt32(blob, 8, mode);
            for (int at = 0; at < 24; at += 4) Marshal.WriteInt32(variant, at, 0);
            Marshal.WriteInt16(variant, 0, BlobType);
            Marshal.WriteInt32(variant, 8, 12);
            Marshal.WriteIntPtr(variant, 16, blob);
            return Await(variant);
        } finally {
            Marshal.FreeHGlobal(variant);
            Marshal.FreeHGlobal(blob);
        }
    }

    // WHY: объекты активации освобождаются здесь же: пока они живы, виртуальное устройство остаётся
    // WHY: занятым, и повторная наводка на то же приложение падает на Initialize с E_UNEXPECTED
    static object Await(IntPtr variant) {
        Guid iid = new Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");
        var waiter = new Waiter();
        IActivateAudioInterfaceAsyncOperation operation;
        ActivateAudioInterfaceAsync(VirtualDevice, ref iid, variant, waiter, out operation);
        if (!waiter.done.WaitOne(WaitMs)) throw new Exception("activation timeout");

        try {
            int result;
            object instance;
            int hr = waiter.operation.GetActivateResult(out result, out instance);
            if (hr != 0) throw new Exception("activation 0x" + hr.ToString("X8"));
            if (result != 0) throw new Exception("process loopback 0x" + result.ToString("X8"));
            return instance;
        } finally {
            Drop(waiter.operation);
            Drop(operation);
            waiter.operation = null;
        }
    }

    static void Drop(object instance) {
        try {
            if (instance != null && Marshal.IsComObject(instance)) Marshal.FinalReleaseComObject(instance);
        } catch {
        }
    }

    // WHY: обработчик обязан быть agile, иначе ActivateAudioInterfaceAsync отвечает E_ILLEGAL_METHOD_CALL
    [ComVisible(true)]
    class Waiter : IActivateAudioInterfaceCompletionHandler, IAgileObject {
        public readonly ManualResetEvent done = new ManualResetEvent(false);
        public IActivateAudioInterfaceAsyncOperation operation;

        public int ActivateCompleted(IActivateAudioInterfaceAsyncOperation finished) {
            operation = finished;
            done.Set();
            return 0;
        }
    }
}

[ComImport, Guid("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAgileObject {
}

[ComImport, Guid("41D949AB-9862-444A-80F6-C261334DA5EB"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IActivateAudioInterfaceCompletionHandler {
    [PreserveSig] int ActivateCompleted(IActivateAudioInterfaceAsyncOperation operation);
}

[ComImport, Guid("72A22D78-CDE4-431D-B8CC-843A71199B6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IActivateAudioInterfaceAsyncOperation {
    [PreserveSig] int GetActivateResult(out int result, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
}

// WHY: процессный захват берёт поток приложения до системного регулятора и от ползунка не зависит,
// WHY: замерено на ровном тоне. Запасной захват устройства идёт после него, там громкость делится обратно
public static class BattleCraftVolume {
    const float Quietest = 0.08f;

    static IAudioEndpointVolume knob;

    public static float System() {
        try {
            if (knob == null) Mount();
            if (knob == null) return 1.0f;

            float level;
            if (knob.GetMasterVolumeLevelScalar(out level) != 0) { knob = null; return 1.0f; }
            return level;
        } catch {
            knob = null;
            return 1.0f;
        }
    }

    public static float Divider() {
        return Math.Max(Quietest, System());
    }

    static void Mount() {
        var enumerator = (IMMDeviceEnumerator) new MMDeviceEnumerator();
        IMMDevice device;
        if (enumerator.GetDefaultAudioEndpoint(0, 0, out device) != 0 || device == null) return;

        Guid iid = typeof(IAudioEndpointVolume).GUID;
        object created;
        if (device.Activate(ref iid, 23, IntPtr.Zero, out created) != 0) return;

        knob = created as IAudioEndpointVolume;
    }
}

[ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioEndpointVolume {
    [PreserveSig] int RegisterControlChangeNotify(IntPtr notify);
    [PreserveSig] int UnregisterControlChangeNotify(IntPtr notify);
    [PreserveSig] int GetChannelCount(out int count);
    [PreserveSig] int SetMasterVolumeLevel(float level, Guid context);
    [PreserveSig] int SetMasterVolumeLevelScalar(float level, Guid context);
    [PreserveSig] int GetMasterVolumeLevel(out float level);
    [PreserveSig] int GetMasterVolumeLevelScalar(out float level);
}
