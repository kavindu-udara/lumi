import { NextResponse } from "next/server";
import { NextRequest } from "next/server";
import { getRequestAuthUser } from "@/lib/auth";

export async function GET(request: NextRequest) {
  const authUser = await getRequestAuthUser(request);

  if (!authUser?.email) {
    return NextResponse.json(
      { message: "Unauthorized", success: false },
      { status: 401 },
    );
  }

  return NextResponse.json(
    {
      message: "Library API is working",
      images: [
        {
          id: "strinngId",
          metadata: { name: "Image 1", description: "This is the first image" },
          url: "https://example.com/image1.jpg",
        },
        {
          id: "strinngId",
          metadata: { name: "Image 2", description: "This is the second image" },
          url: "https://example.com/image2.jpg",
        },
        {
          id: "strinngId",
          metadata: { name: "Image 3", description: "This is the third image" },
          url: "https://example.com/image3.jpg",
        },
      ],
      success: true,
    },
    { status: 200 },
  );
}