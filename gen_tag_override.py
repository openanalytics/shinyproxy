#!/usr/bin/env python
#
# ShinyProxy
#
# Copyright (C) 2016-2017 Open Analytics
#
# ===========================================================================
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the Apache License as published by
# The Apache Software Foundation, either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# Apache License for more details.
#
# You should have received a copy of the Apache License
# along with this program.  If not, see <http://www.apache.org/licenses/>
#

assert sys.version_info >= (3, 2)

if len(sys.argv) < 4:
    print("Usage: ./gen_tag_override.py <secret file> <app name> <tag name> [expiry unix time]")
    print("Note: Unix time is seconds since Jan 1, 1970 UTC")
    print("To get unix time, you can use https://www.unixtimestamp.com/")
    exit()

with open(sys.argv[1], "rb") as f:
    SECRET = f.read()
APP_NAME = sys.argv[2]
TAG_NAME = sys.argv[3]
if len(sys.argv) >= 5:
    EXPIRY_TIMESTAMP = int(sys.argv[4]) * 1000
else:
    EXPIRY_TIMESTAMP = 0


HASHER = hashlib.sha256()
HASHER.update(APP_NAME.encode())
HASHER.update(b"\0")
HASHER.update(TAG_NAME.encode())
HASHER.update(b"\0")
HASHER.update(EXPIRY_TIMESTAMP.to_bytes(8, byteorder='big'))
HASHER.update(b"\0")
HASHER.update(SECRET)

print(base64.urlsafe_b64encode(HASHER.digest()).decode())
