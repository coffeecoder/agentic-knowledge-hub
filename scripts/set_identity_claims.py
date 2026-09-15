#!/usr/bin/env python3
"""Administrative helper for Cloud Shell; never used by the Java application."""
import argparse
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request


def application_claims(tenant: str, group: str, source: str, access: str) -> dict:
    return {
        "tenant": tenant,
        "groups": [group],
        "permissions": ["knowledge:read", "knowledge:write"] if access == "publisher" else ["knowledge:read"],
        "write_sources": [source] if access == "publisher" else [],
        "assignable_groups": [group] if access == "publisher" else [],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True)
    parser.add_argument("--uid", required=True)
    parser.add_argument("--tenant", default="local")
    parser.add_argument("--group", default="support")
    parser.add_argument("--source", default="manual-text")
    parser.add_argument("--access", choices=["reader", "publisher"], required=True)
    parser.add_argument("--apply", action="store_true", help="Apply and read back; otherwise preview only")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", args.project):
        parser.error("Invalid project ID")
    if not args.uid or len(args.uid) > 128 or any(not v.strip() for v in [args.tenant, args.group, args.source]):
        parser.error("UID and application scope must be nonempty")
    desired = application_claims(args.tenant, args.group, args.source, args.access)
    if not args.apply:
        print(json.dumps({"akh": desired}, indent=2))
        print("Preview only. Add --apply to update the specified user.")
        return
    token = subprocess.run(["gcloud", "auth", "print-access-token"], check=True,
                           capture_output=True, text=True, timeout=60).stdout.strip()
    if not token:
        raise ValueError("No administrator credential")
    base = f"https://identitytoolkit.googleapis.com/v1/projects/{args.project}/accounts:"

    def request(operation: str, payload: dict) -> dict:
        req = urllib.request.Request(base + operation, data=json.dumps(payload).encode(),
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.load(response)

    def read_claims() -> dict:
        users = request("lookup", {"localId": [args.uid]}).get("users", [])
        if len(users) != 1 or users[0].get("localId") != args.uid:
            raise ValueError("User not found")
        claims = json.loads(users[0].get("customAttributes", "{}"))
        if not isinstance(claims, dict):
            raise ValueError("Invalid existing custom claims")
        return claims

    claims = read_claims()
    claims["akh"] = desired
    encoded = json.dumps(claims, separators=(",", ":"), ensure_ascii=False)
    if len(encoded.encode()) > 1000:
        raise ValueError("Custom claims exceed 1000 bytes")
    # Preserve other namespaces; accounts:update replaces the entire customAttributes object.
    request("update", {"localId": args.uid, "customAttributes": encoded})
    if read_claims().get("akh") != desired:
        raise ValueError("Read-back verification failed")
    print("Application permissions verified. Sign in again to obtain a new ID token.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError):
        sys.exit("Permission setup failed. Check gcloud sign-in, project, UID and firebaseauth.users.get/update access. No credentials or response details were printed.")
