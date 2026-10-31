import os
import re

EXTENSIONS = (".txt", ".csv", ".json")
MAX_SIZE = 2 * 1024 * 1024

def scan_folder(folder, pattern):
    regex = re.compile(pattern)
    total = 0
    matches = 0

    print("\n===== REGEXWATCH =====")
    print("[+] Scanner started")

    for root, dirs, files in os.walk(folder):
        for filename in files:
            if not filename.lower().endswith(EXTENSIONS):
                continue

            path = os.path.join(root, filename)

            try:
                if os.path.getsize(path) > MAX_SIZE:
                    continue

                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()

                total += 1
                found = regex.findall(content)

                if found:
                    matches += len(found)
                    print(f"\n[FILE] {filename}")

                    for match in found:
                        print(f"[MATCH] {match}")

            except (OSError, UnicodeError):
                print(f"[SKIPPED] {filename}")

    print("\n===== SCAN COMPLETE =====")
    print(f"Files scanned: {total}")
    print(f"Matches found: {matches}")

if __name__ == "__main__":
    folder = input("Folder to scan: ")
    pattern = input("Enter regex pattern: ")

    try:
        if os.path.isdir(folder):
            scan_folder(folder, pattern)
        else:
            print("Invalid folder.")
    except re.error as error:
        print("Invalid regex:", error)
