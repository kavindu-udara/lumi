export async function POST(request: Request) {
    const { email, password } = await request.json();

    if(!email || !password) {
        return new Response("Email and password are required", { status: 400 });
    }

    // Here you would normally check the email and password against your database
    if (email === "admin@example.com" && password === "password") {
        return new Response(JSON.stringify({success: true, message: "Login successful"}), { status: 200 });
    } else {
        return new Response(JSON.stringify({success: false, message: "Invalid credentials"}), { status: 401 });
    }
}
