using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class BattleCraftFrames {
    const int MediaFoundationVersion = 0x00020070;
    const int FirstVideoStream = unchecked((int)0xFFFFFFFC);
    const int AllStreams = unchecked((int)0xFFFFFFFE);
    const int WholeSource = unchecked((int)0xFFFFFFFF);
    const int ReaderError = 0x1;
    const int EndOfStream = 0x2;
    const int FormatChanged = 0x20;
    const long TicksPerSecond = 10000000L;
    const int AreaBytes = 16;
    const long RateScale = 1000L;
    const int ProbeSamples = 16;
    const int TailFrames = 2;
    const int Backlog = 4;
    const int BacklogWaitMs = 5;
    const int BacklogLimitMs = 120000;
    const int StallLimitMs = 30000;
    const int WatchStepMs = 250;

    static readonly Stopwatch clock = Stopwatch.StartNew();
    static Process parent;
    static long progressAt;

    [DllImport("mfplat.dll")] static extern int MFStartup(int version, int flags);
    [DllImport("mfplat.dll")] static extern int MFShutdown();
    [DllImport("mfplat.dll")] static extern int MFCreateAttributes(out IMFAttributes attributes, int size);
    [DllImport("mfplat.dll")] static extern int MFCreateMediaType(out IMFMediaType type);
    [DllImport("mfreadwrite.dll", CharSet = CharSet.Unicode)]
    static extern int MFCreateSourceReaderFromURL(string url, IMFAttributes attributes, out IMFSourceReader reader);
    [DllImport("ole32.dll")] static extern int PropVariantClear(IntPtr variant);

    public static void Extract(string source, string folder, int maxFps, int maxSeconds, int parentPid) {
        StartWatch(parentPid);
        try {
            Check(MFStartup(MediaFoundationVersion, 0), "startup");
            Say("{\"done\":" + Convert(source, folder, maxFps, maxSeconds) + "}");
        } catch (Exception error) {
            Say("{\"error\":\"" + Escape(error.Message) + "\"}");
        } finally {
            MFShutdown();
        }
    }

    static int Convert(string source, string folder, int maxFps, int maxSeconds) {
        double measured = MeasureFps(source);
        Clip clip = Open(source, measured, maxFps, maxSeconds);
        try {
            Directory.CreateDirectory(folder);
            Say(clip.Describe());
            int stored = Pump(clip, folder);
            if (stored == 0) throw new InvalidOperationException("decode");
            return stored;
        } finally {
            Release(clip.Reader);
        }
    }

    static void StartWatch(int parentPid) {
        Touch();
        try {
            if (parentPid != 0) parent = Process.GetProcessById(parentPid);
        } catch (ArgumentException) {
            Environment.Exit(0);
        }
        Thread watchdog = new Thread(Watch);
        watchdog.IsBackground = true;
        watchdog.Start();
    }

    // WHY: декодер Windows на профиле, который он не умеет (H.264 10 бит), не отдаёт ошибку, а
    // WHY: навсегда повисает в ReadSample. Сторож снимает процесс, когда кадры перестали идти,
    // WHY: и когда игра закрылась посреди импорта
    static void Watch() {
        while (true) {
            Thread.Sleep(WatchStepMs);
            if (parent != null && parent.HasExited) Environment.Exit(0);
            if (clock.ElapsedMilliseconds - Interlocked.Read(ref progressAt) <= StallLimitMs) continue;

            Say("{\"error\":\"stalled\"}");
            Environment.Exit(3);
        }
    }

    static void Touch() {
        Interlocked.Exchange(ref progressAt, clock.ElapsedMilliseconds);
    }

    // WHY: расширенная обработка втрое быстрее простой на 4K, но подгоняет кадры под частоту из
    // WHY: типа потока, а у mkv и webm она врёт вдвое. Поэтому частота выхода задаётся явно,
    // WHY: измеренной, а простая обработка остаётся запасной, если расширенная не взялась за формат.
    // WHY: Поворот из метаданных телефона расширенная применяет сама, простая нет
    static IMFSourceReader OpenReader(string source, double fps, out bool rotates) {
        try {
            IMFSourceReader advanced = OpenReader(source, Keys.AdvancedVideoProcessing, fps);
            rotates = true;
            return advanced;
        } catch (InvalidOperationException) {
            rotates = false;
            return OpenReader(source, Keys.VideoProcessing, 0.0);
        }
    }

    static IMFSourceReader OpenReader(string source, Guid processing, double fps) {
        IMFAttributes attributes;
        Check(MFCreateAttributes(out attributes, 1), "attributes");
        Check(attributes.SetUINT32(ref processing, 1), "processing");
        IMFSourceReader reader = OpenVideo(source, attributes);
        Release(attributes);
        try {
            AskForRgb(reader, fps);
            return reader;
        } catch (InvalidOperationException) {
            Release(reader);
            throw;
        }
    }

    static IMFSourceReader OpenVideo(string source, IMFAttributes attributes) {
        IMFSourceReader reader;
        Check(MFCreateSourceReaderFromURL(source, attributes, out reader), "open");
        try {
            Check(reader.SetStreamSelection(AllStreams, false), "deselect");
            Check(reader.SetStreamSelection(FirstVideoStream, true), "video");
            return reader;
        } catch (InvalidOperationException) {
            Release(reader);
            throw;
        }
    }

    static void AskForRgb(IMFSourceReader reader, double fps) {
        IMFMediaType wanted;
        Check(MFCreateMediaType(out wanted), "media type");
        try {
            Guid major = Keys.MajorType;
            Guid video = Keys.Video;
            Guid subtype = Keys.Subtype;
            Guid rgb = Keys.Rgb32;
            Guid rate = Keys.FrameRate;
            Check(wanted.SetGUID(ref major, ref video), "major type");
            Check(wanted.SetGUID(ref subtype, ref rgb), "subtype");
            if (fps > 0.0) Check(wanted.SetUINT64(ref rate, ((long)Math.Round(fps * RateScale) << 32) | RateScale), "rate");
            Check(reader.SetCurrentMediaType(FirstVideoStream, IntPtr.Zero, wanted), "codec");
        } finally {
            Release(wanted);
        }
    }

    static Clip Open(string source, double measuredFps, int maxFps, int maxSeconds) {
        bool rotates;
        Clip clip = new Clip(OpenReader(source, measuredFps, out rotates));
        Measure(clip);
        clip.Rotation = rotates ? 0 : RotationOf(clip.Reader);
        clip.SourceTicks = DurationOf(clip.Reader);
        clip.SourceFps = measuredFps > 0.0 ? measuredFps : clip.DeclaredFps;
        clip.Fps = Math.Min(clip.SourceFps > 0.0 ? clip.SourceFps : maxFps, maxFps);
        long kept = Math.Min(clip.SourceTicks, maxSeconds * TicksPerSecond);
        clip.Count = Math.Max(1, (int)Math.Floor(kept * clip.Fps / TicksPerSecond + 0.01));
        return clip;
    }

    static void Measure(Clip clip) {
        IMFMediaType current;
        Check(clip.Reader.GetCurrentMediaType(FirstVideoStream, out current), "current type");
        try {
            long size = Pair(current, Keys.FrameSize);
            clip.DecodedWidth = (int)(size >> 32);
            long rate = Pair(current, Keys.FrameRate);
            long denominator = rate & 0xFFFFFFFFL;
            if (denominator != 0) clip.DeclaredFps = (double)(rate >> 32) / denominator;
            clip.Adopt(ApertureOf(current, clip.DecodedWidth, (int)(size & 0xFFFFFFFFL)));
        } finally {
            Release(current);
        }
    }

    // WHY: декодер отдаёт кадр, выровненный до макроблока (1920x1088), а видимая часть лежит в
    // WHY: апертуре: без неё внизу кадра оставалась полоса мусора
    static int[] ApertureOf(IMFMediaType type, int width, int height) {
        int[] area = Area(type, Keys.MinimumDisplayAperture);
        if (area == null) area = Area(type, Keys.GeometricAperture);
        return area ?? new int[] { 0, 0, width, height };
    }

    static int[] Area(IMFMediaType type, Guid key) {
        IntPtr block = Marshal.AllocCoTaskMem(AreaBytes);
        try {
            int written;
            if (type.GetBlob(ref key, block, AreaBytes, out written) < 0 || written < AreaBytes) return null;
            return new int[] {
                Marshal.ReadInt16(block, 2), Marshal.ReadInt16(block, 6),
                Marshal.ReadInt32(block, 8), Marshal.ReadInt32(block, 12)
            };
        } finally {
            Marshal.FreeCoTaskMem(block);
        }
    }

    static long Pair(IMFMediaType type, Guid key) {
        long value;
        return type.GetUINT64(ref key, out value) == 0 ? value : 0L;
    }

    static long DurationOf(IMFSourceReader reader) {
        IntPtr variant = Marshal.AllocCoTaskMem(32);
        try {
            for (int offset = 0; offset < 32; offset += 8) Marshal.WriteInt64(variant, offset, 0L);
            Guid key = Keys.Duration;
            Check(reader.GetPresentationAttribute(WholeSource, ref key, variant), "duration");
            long ticks = Marshal.ReadInt64(variant, 8);
            PropVariantClear(variant);
            return ticks;
        } finally {
            Marshal.FreeCoTaskMem(variant);
        }
    }

    // WHY: частота из типа потока врёт для mkv и webm, поэтому она меряется по меткам первых кадров.
    // WHY: Кадры читаются сжатыми, без декодера: метки идут в порядке декодирования и сортируются
    static double MeasureFps(string source) {
        IMFSourceReader reader = OpenVideo(source, null);
        long[] stamps = new long[ProbeSamples];
        int count = 0;
        try {
            while (count < ProbeSamples) {
                int stream, flags;
                long at;
                IMFSample sample;
                Check(reader.ReadSample(FirstVideoStream, 0, out stream, out flags, out at, out sample), "decode");
                Touch();
                if (sample != null) stamps[count++] = at;
                Release(sample);
                if ((flags & (EndOfStream | ReaderError)) != 0) break;
            }
        } finally {
            Release(reader);
        }
        return FpsOf(stamps, count);
    }

    // WHY: среди первых кадров в порядке декодирования бывают дыры (кадр уехал за пределы выборки),
    // WHY: а webm хранит метки с точностью до миллисекунды. Шаг берётся средним по шагам, близким к
    // WHY: медиане: дыры отсекаются, а округление меток усредняется
    static double FpsOf(long[] stamps, int count) {
        if (count < 3) return 0.0;

        Array.Sort(stamps, 0, count);
        long[] steps = new long[count - 1];
        for (int index = 0; index < steps.Length; index++) steps[index] = stamps[index + 1] - stamps[index];
        Array.Sort(steps);
        long median = steps[steps.Length / 2];
        long sum = 0L;
        int used = 0;
        foreach (long step in steps) {
            if (Math.Abs(step - median) * 4 > median) continue;
            sum += step;
            used++;
        }
        return median > 0 && used > 0 ? (double)TicksPerSecond * used / sum : 0.0;
    }

    static int RotationOf(IMFSourceReader reader) {
        IMFMediaType native;
        if (reader.GetNativeMediaType(FirstVideoStream, 0, out native) < 0) return 0;
        try {
            Guid key = Keys.Rotation;
            int degrees;
            return native.GetUINT32(ref key, out degrees) == 0 ? degrees : 0;
        } finally {
            Release(native);
        }
    }

    // WHY: у wmv метка последнего кадра стоит на кадр раньше длительности контейнера, и без запаса
    // WHY: в хвосте ролик терял последний кадр. Число кадров всё равно ограничено сверху Count
    static int Pump(Clip clip, string folder) {
        int emitted = 0;
        IMFSample held = null;
        long heldAt = 0L;
        while (emitted < clip.Count) {
            int stream, flags;
            long at;
            IMFSample sample;
            Check(clip.Reader.ReadSample(FirstVideoStream, 0, out stream, out flags, out at, out sample), "decode");
            Touch();
            if ((flags & ReaderError) != 0) throw new InvalidOperationException("decode");
            if ((flags & FormatChanged) != 0) Measure(clip);
            if (sample == null) {
                if ((flags & EndOfStream) != 0) break;
                continue;
            }
            if (held == null) clip.Origin = at;
            else emitted = EmitUntil(clip, held, heldAt + (at - heldAt) / 2, emitted, folder);
            Release(held);
            held = sample;
            heldAt = at;
        }
        if (held != null) emitted = EmitUntil(clip, held, heldAt + TailFrames * clip.SourceFrameTicks(), emitted, folder);
        Release(held);
        return emitted;
    }

    // WHY: кадр выхода берёт ближайший по времени кадр исходника: граница между соседними это
    // WHY: середина их меток, поэтому дрожание меток в контейнере не даёт ни дублей, ни пропусков.
    // WHY: Время считается от первого кадра: Media Foundation не применяет список правок mp4, и
    // WHY: ролик с B-кадрами начинается с метки в несколько кадров, а не с нуля
    static int EmitUntil(Clip clip, IMFSample sample, long until, int emitted, string folder) {
        while (emitted < clip.Count && clip.Origin + clip.OutputTicks(emitted) < until) {
            WaitForRoom(folder, emitted);
            Store(clip, sample, Path.Combine(folder, FrameName(emitted)));
            Say("{\"frame\":" + emitted + "}");
            emitted++;
        }
        return emitted;
    }

    static string FrameName(int index) {
        return index.ToString("D6", CultureInfo.InvariantCulture) + ".raw";
    }

    // WHY: кадр 4K весит 33 МБ, а Java жмёт его в JPEG медленнее, чем декодер отдаёт следующий:
    // WHY: без придержки двадцать секунд ролика легли бы на диск двадцатью гигабайтами
    static void WaitForRoom(string folder, int index) {
        if (index < Backlog) return;

        string oldest = Path.Combine(folder, FrameName(index - Backlog));
        Stopwatch watch = Stopwatch.StartNew();
        while (File.Exists(oldest)) {
            if (watch.ElapsedMilliseconds > BacklogLimitMs) throw new TimeoutException("consumer");
            Touch();
            Thread.Sleep(BacklogWaitMs);
        }
    }

    static void Store(Clip clip, IMFSample sample, string path) {
        IMFMediaBuffer buffer;
        Check(sample.GetBufferByIndex(0, out buffer), "buffer");
        try {
            IMF2DBuffer plane = buffer as IMF2DBuffer;
            if (plane != null) CopyPlane(clip, plane);
            else CopyFlat(clip, buffer);
        } finally {
            Release(buffer);
        }
        using (FileStream file = new FileStream(path, FileMode.Create, FileAccess.Write, FileShare.None, 1 << 20)) {
            file.Write(clip.Frame, 0, clip.Frame.Length);
        }
    }

    static void CopyPlane(Clip clip, IMF2DBuffer plane) {
        IntPtr top;
        int pitch;
        Check(plane.Lock2D(out top, out pitch), "lock");
        try {
            clip.CopyRows(top, pitch);
        } finally {
            plane.Unlock2D();
        }
    }

    static void CopyFlat(Clip clip, IMFMediaBuffer buffer) {
        IntPtr start;
        int capacity, length;
        Check(buffer.Lock(out start, out capacity, out length), "lock");
        try {
            clip.CopyRows(start, clip.DecodedWidth * 4);
        } finally {
            buffer.Unlock();
        }
    }

    static void Release(object target) {
        if (target != null) Marshal.ReleaseComObject(target);
    }

    static void Check(int result, string step) {
        if (result < 0) throw new InvalidOperationException(step + " 0x" + result.ToString("X8", CultureInfo.InvariantCulture));
    }

    static void Say(string line) {
        Console.Out.WriteLine(line);
        Console.Out.Flush();
    }

    static string Escape(string value) {
        StringBuilder text = new StringBuilder(value.Length + 8);
        foreach (char symbol in value) {
            if (symbol == '"' || symbol == '\\') text.Append('\\').Append(symbol);
            else if (symbol < ' ') text.Append(' ');
            else text.Append(symbol);
        }
        return text.ToString();
    }

    sealed class Clip {
        public readonly IMFSourceReader Reader;
        public int Width;
        public int Height;
        public int DecodedWidth;
        public int OffsetX;
        public int OffsetY;
        public int VisibleWidth;
        public int VisibleHeight;
        public double DeclaredFps;
        public double SourceFps;
        public double Fps;
        public long SourceTicks;
        public int Count;
        public long Origin;
        public int Rotation;
        public byte[] Frame;

        public Clip(IMFSourceReader reader) {
            Reader = reader;
        }

        public void Adopt(int[] area) {
            if (area[2] <= 0 || area[3] <= 0) throw new InvalidOperationException("size");
            if (Frame == null) {
                Width = area[2];
                Height = area[3];
                Frame = new byte[Width * Height * 4];
            }
            OffsetX = Math.Max(0, area[0]);
            OffsetY = Math.Max(0, area[1]);
            VisibleWidth = Math.Min(area[2], Width);
            VisibleHeight = Math.Min(area[3], Height);
        }

        public void CopyRows(IntPtr top, int pitch) {
            long start = top.ToInt64() + (long)OffsetY * pitch + OffsetX * 4L;
            for (int y = 0; y < VisibleHeight; y++) {
                Marshal.Copy(new IntPtr(start + (long)y * pitch), Frame, y * Width * 4, VisibleWidth * 4);
            }
        }

        public long OutputTicks(int index) {
            return (long)Math.Round(index * TicksPerSecond / Fps);
        }

        public long SourceFrameTicks() {
            return (long)Math.Round(TicksPerSecond / (SourceFps > 0.0 ? SourceFps : Fps));
        }

        public string Describe() {
            return string.Format(CultureInfo.InvariantCulture,
                "{{\"clip\":1,\"width\":{0},\"height\":{1},\"fps\":{2:R},\"count\":{3},\"ms\":{4},\"rotation\":{5}}}",
                Width, Height, Fps, Count, SourceTicks / 10000L, Rotation);
        }
    }

    static class Keys {
        public static readonly Guid VideoProcessing = new Guid("fb394f3d-ccf1-42ee-bbb3-f9b845d5681d");
        public static readonly Guid AdvancedVideoProcessing = new Guid("0f81da2c-b537-4672-a8b2-a681b17307a3");
        public static readonly Guid MajorType = new Guid("48eba18e-f8c9-4687-bf11-0a74c9f96a8f");
        public static readonly Guid Subtype = new Guid("f7e34c9a-42e8-4714-b74b-cb29d72c35e5");
        public static readonly Guid Video = new Guid("73646976-0000-0010-8000-00aa00389b71");
        public static readonly Guid Rgb32 = new Guid("00000016-0000-0010-8000-00aa00389b71");
        public static readonly Guid FrameSize = new Guid("1652c33d-d6b2-4012-b834-72030849a37d");
        public static readonly Guid FrameRate = new Guid("c459a2e8-3d2c-4e44-b132-fee5156c7bb0");
        public static readonly Guid MinimumDisplayAperture = new Guid("d7388766-18fe-48c6-a177-ee894867c8c4");
        public static readonly Guid GeometricAperture = new Guid("66758743-7e5f-400d-980a-aa8596c85696");
        public static readonly Guid Rotation = new Guid("c380465d-2271-428c-9b83-ecea3b4a85c1");
        public static readonly Guid Duration = new Guid("6c990d33-bb8e-477a-8598-0d5d96fcd88a");
    }
}

[ComImport, Guid("2cd2d921-c447-44a7-a13c-4adabfc247e3"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMFAttributes {
    [PreserveSig] int GetItem(ref Guid key, IntPtr value);
    [PreserveSig] int GetItemType(ref Guid key, out int type);
    [PreserveSig] int CompareItem(ref Guid key, IntPtr value, out bool same);
    [PreserveSig] int Compare(IntPtr other, int match, out bool same);
    [PreserveSig] int GetUINT32(ref Guid key, out int value);
    [PreserveSig] int GetUINT64(ref Guid key, out long value);
    [PreserveSig] int GetDouble(ref Guid key, out double value);
    [PreserveSig] int GetGUID(ref Guid key, out Guid value);
    [PreserveSig] int GetStringLength(ref Guid key, out int length);
    [PreserveSig] int GetString(ref Guid key, IntPtr value, int size, IntPtr length);
    [PreserveSig] int GetAllocatedString(ref Guid key, out IntPtr value, out int length);
    [PreserveSig] int GetBlobSize(ref Guid key, out int size);
    [PreserveSig] int GetBlob(ref Guid key, IntPtr value, int size, out int written);
    [PreserveSig] int GetAllocatedBlob(ref Guid key, out IntPtr value, out int size);
    [PreserveSig] int GetUnknown(ref Guid key, ref Guid iid, out IntPtr value);
    [PreserveSig] int SetItem(ref Guid key, IntPtr value);
    [PreserveSig] int DeleteItem(ref Guid key);
    [PreserveSig] int DeleteAllItems();
    [PreserveSig] int SetUINT32(ref Guid key, int value);
    [PreserveSig] int SetUINT64(ref Guid key, long value);
    [PreserveSig] int SetDouble(ref Guid key, double value);
    [PreserveSig] int SetGUID(ref Guid key, ref Guid value);
}

[ComImport, Guid("44ae0fa8-ea31-4109-8d2e-4cae4997c555"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMFMediaType {
    [PreserveSig] int GetItem(ref Guid key, IntPtr value);
    [PreserveSig] int GetItemType(ref Guid key, out int type);
    [PreserveSig] int CompareItem(ref Guid key, IntPtr value, out bool same);
    [PreserveSig] int Compare(IntPtr other, int match, out bool same);
    [PreserveSig] int GetUINT32(ref Guid key, out int value);
    [PreserveSig] int GetUINT64(ref Guid key, out long value);
    [PreserveSig] int GetDouble(ref Guid key, out double value);
    [PreserveSig] int GetGUID(ref Guid key, out Guid value);
    [PreserveSig] int GetStringLength(ref Guid key, out int length);
    [PreserveSig] int GetString(ref Guid key, IntPtr value, int size, IntPtr length);
    [PreserveSig] int GetAllocatedString(ref Guid key, out IntPtr value, out int length);
    [PreserveSig] int GetBlobSize(ref Guid key, out int size);
    [PreserveSig] int GetBlob(ref Guid key, IntPtr value, int size, out int written);
    [PreserveSig] int GetAllocatedBlob(ref Guid key, out IntPtr value, out int size);
    [PreserveSig] int GetUnknown(ref Guid key, ref Guid iid, out IntPtr value);
    [PreserveSig] int SetItem(ref Guid key, IntPtr value);
    [PreserveSig] int DeleteItem(ref Guid key);
    [PreserveSig] int DeleteAllItems();
    [PreserveSig] int SetUINT32(ref Guid key, int value);
    [PreserveSig] int SetUINT64(ref Guid key, long value);
    [PreserveSig] int SetDouble(ref Guid key, double value);
    [PreserveSig] int SetGUID(ref Guid key, ref Guid value);
}

[ComImport, Guid("70ae66f2-c809-4e4f-8915-bdcb406b7993"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMFSourceReader {
    [PreserveSig] int GetStreamSelection(int stream, out bool selected);
    [PreserveSig] int SetStreamSelection(int stream, bool selected);
    [PreserveSig] int GetNativeMediaType(int stream, int index, out IMFMediaType type);
    [PreserveSig] int GetCurrentMediaType(int stream, out IMFMediaType type);
    [PreserveSig] int SetCurrentMediaType(int stream, IntPtr reserved, IMFMediaType type);
    [PreserveSig] int SetCurrentPosition(ref Guid format, IntPtr position);
    [PreserveSig] int ReadSample(int stream, int control, out int actualStream, out int flags, out long timestamp, out IMFSample sample);
    [PreserveSig] int Flush(int stream);
    [PreserveSig] int GetServiceForStream(int stream, ref Guid service, ref Guid iid, out IntPtr target);
    [PreserveSig] int GetPresentationAttribute(int stream, ref Guid key, IntPtr value);
}

[ComImport, Guid("c40a00f2-b93a-4d80-ae8c-5a1c634f58e4"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMFSample {
    [PreserveSig] int GetItem(ref Guid key, IntPtr value);
    [PreserveSig] int GetItemType(ref Guid key, out int type);
    [PreserveSig] int CompareItem(ref Guid key, IntPtr value, out bool same);
    [PreserveSig] int Compare(IntPtr other, int match, out bool same);
    [PreserveSig] int GetUINT32(ref Guid key, out int value);
    [PreserveSig] int GetUINT64(ref Guid key, out long value);
    [PreserveSig] int GetDouble(ref Guid key, out double value);
    [PreserveSig] int GetGUID(ref Guid key, out Guid value);
    [PreserveSig] int GetStringLength(ref Guid key, out int length);
    [PreserveSig] int GetString(ref Guid key, IntPtr value, int size, IntPtr length);
    [PreserveSig] int GetAllocatedString(ref Guid key, out IntPtr value, out int length);
    [PreserveSig] int GetBlobSize(ref Guid key, out int size);
    [PreserveSig] int GetBlob(ref Guid key, IntPtr value, int size, out int written);
    [PreserveSig] int GetAllocatedBlob(ref Guid key, out IntPtr value, out int size);
    [PreserveSig] int GetUnknown(ref Guid key, ref Guid iid, out IntPtr value);
    [PreserveSig] int SetItem(ref Guid key, IntPtr value);
    [PreserveSig] int DeleteItem(ref Guid key);
    [PreserveSig] int DeleteAllItems();
    [PreserveSig] int SetUINT32(ref Guid key, int value);
    [PreserveSig] int SetUINT64(ref Guid key, long value);
    [PreserveSig] int SetDouble(ref Guid key, double value);
    [PreserveSig] int SetGUID(ref Guid key, ref Guid value);
    [PreserveSig] int SetString(ref Guid key, IntPtr value);
    [PreserveSig] int SetBlob(ref Guid key, IntPtr value, int size);
    [PreserveSig] int SetUnknown(ref Guid key, IntPtr value);
    [PreserveSig] int LockStore();
    [PreserveSig] int UnlockStore();
    [PreserveSig] int GetCount(out int count);
    [PreserveSig] int GetItemByIndex(int index, out Guid key, IntPtr value);
    [PreserveSig] int CopyAllItems(IntPtr target);
    [PreserveSig] int GetSampleFlags(out int flags);
    [PreserveSig] int SetSampleFlags(int flags);
    [PreserveSig] int GetSampleTime(out long time);
    [PreserveSig] int SetSampleTime(long time);
    [PreserveSig] int GetSampleDuration(out long duration);
    [PreserveSig] int SetSampleDuration(long duration);
    [PreserveSig] int GetBufferCount(out int count);
    [PreserveSig] int GetBufferByIndex(int index, out IMFMediaBuffer buffer);
}

[ComImport, Guid("045fa593-8799-42b8-bc8d-8968c6453507"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMFMediaBuffer {
    [PreserveSig] int Lock(out IntPtr start, out int capacity, out int length);
    [PreserveSig] int Unlock();
}

[ComImport, Guid("7dc9d5f9-9ed9-44ec-9bbf-0600bb589fbb"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMF2DBuffer {
    [PreserveSig] int Lock2D(out IntPtr top, out int pitch);
    [PreserveSig] int Unlock2D();
}
