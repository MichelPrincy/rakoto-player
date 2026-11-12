using System;
using System.IO;
using System.Security.Cryptography;
class HashCheck {
  static int Main(string[] args) {
    try {
      if (args.Length != 2 || args[1].Length != 64) return 2;
      using (var input = File.OpenRead(args[0]))
      using (var sha = SHA256.Create()) {
        string actual = BitConverter.ToString(sha.ComputeHash(input)).Replace("-", "");
        return String.Equals(actual, args[1], StringComparison.OrdinalIgnoreCase) ? 0 : 3;
      }
    } catch { return 4; }
  }
}
