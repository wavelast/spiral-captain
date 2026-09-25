using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

static class Portable
{
    const string FilesFolder = "Spiral Captain Files";
    const string DataFolder = "Spiral Captain Data";
    const string AppExe = "Spiral Captain.exe";
    const string BuildMarker = "build.txt";

    [STAThread]
    static int Main(string[] args)
    {
        Application.EnableVisualStyles();
        string self = Assembly.GetExecutingAssembly().Location;
        string home = Path.GetDirectoryName(self);
        string files = Path.Combine(home, FilesFolder);
        string data = Path.Combine(home, DataFolder);
        try
        {
            string build = ReadResource(BuildMarker).Trim();
            string marker = Path.Combine(files, BuildMarker);
            if (!File.Exists(marker) || File.ReadAllText(marker).Trim() != build)
            {
                Unpack(self, files, build);
            }
            Directory.CreateDirectory(data);
            ProcessStartInfo start = new ProcessStartInfo(Path.Combine(files, AppExe));
            start.Arguments = "\"--data=" + data + "\"";
            start.WorkingDirectory = files;
            start.UseShellExecute = false;
            Process.Start(start);
            return 0;
        }
        catch (IOException)
        {
            Fail("Close Spiral Captain, then start it again so it can finish setting up.");
            return 1;
        }
        catch (UnauthorizedAccessException)
        {
            Fail("Spiral Captain cannot write next to itself. Move it to a folder you can "
                + "write to, such as your Documents or Desktop, and start it again.");
            return 1;
        }
        catch (Exception failure)
        {
            Fail("Spiral Captain could not start: " + failure.Message);
            return 1;
        }
    }

    static string ReadResource(string name)
    {
        using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
        using (StreamReader reader = new StreamReader(stream))
        {
            return reader.ReadToEnd();
        }
    }

    static void Unpack(string self, string files, string build)
    {
        Form window = new Form();
        window.Text = "Spiral Captain";
        window.FormBorderStyle = FormBorderStyle.FixedDialog;
        window.MaximizeBox = false;
        window.MinimizeBox = false;
        window.StartPosition = FormStartPosition.CenterScreen;
        window.ClientSize = new Size(380, 84);
        window.Icon = Icon.ExtractAssociatedIcon(self);
        Label label = new Label();
        label.Text = "Setting up Spiral Captain...";
        label.SetBounds(16, 14, 348, 20);
        ProgressBar bar = new ProgressBar();
        bar.SetBounds(16, 42, 348, 20);
        window.Controls.Add(label);
        window.Controls.Add(bar);

        Exception problem = null;
        window.Shown += delegate
        {
            Thread worker = new Thread(delegate()
            {
                try
                {
                    Extract(files, build, delegate(int done, int total)
                    {
                        window.BeginInvoke((MethodInvoker) delegate
                        {
                            bar.Maximum = total;
                            bar.Value = done;
                        });
                    });
                }
                catch (Exception failure)
                {
                    problem = failure;
                }
                window.BeginInvoke((MethodInvoker) window.Close);
            });
            worker.IsBackground = true;
            worker.Start();
        };
        Application.Run(window);
        if (problem != null)
        {
            throw problem;
        }
    }

    static void Extract(string files, string build, Action<int, int> progress)
    {
        string staging = files + ".new";
        if (Directory.Exists(staging))
        {
            Directory.Delete(staging, true);
        }
        string root = Path.GetFullPath(staging) + Path.DirectorySeparatorChar;
        using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("app.zip"))
        using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
        {
            int total = archive.Entries.Count;
            int done = 0;
            foreach (ZipArchiveEntry entry in archive.Entries)
            {
                string target = Path.GetFullPath(Path.Combine(staging, entry.FullName));
                if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                {
                    throw new InvalidDataException("the bundled files are damaged");
                }
                if (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\"))
                {
                    Directory.CreateDirectory(target);
                }
                else
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(target));
                    entry.ExtractToFile(target, true);
                }
                done++;
                progress(done, total);
            }
        }
        File.WriteAllText(Path.Combine(staging, BuildMarker), build);
        if (Directory.Exists(files))
        {
            Directory.Delete(files, true);
        }
        Directory.Move(staging, files);
    }

    static void Fail(string message)
    {
        MessageBox.Show(message, "Spiral Captain", MessageBoxButtons.OK, MessageBoxIcon.Warning);
    }
}
