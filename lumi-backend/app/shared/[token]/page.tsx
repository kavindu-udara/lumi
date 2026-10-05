import { notFound } from "next/navigation";
import { getPublicImages, resolvePublicAlbum } from "@/lib/sharing/public-album";

type PageProps = { params: Promise<{ token: string }> };

export const dynamic = "force-dynamic";

export default async function SharedAlbumPage({ params }: PageProps) {
  const { token } = await params;
  const resolved = await resolvePublicAlbum(token);
  if (!resolved) notFound();

  const images = await getPublicImages(resolved.album.id);
  const encodedToken = encodeURIComponent(token);
  return (
    <main style={{ maxWidth: 1100, margin: "0 auto", padding: "32px 20px", fontFamily: "system-ui, sans-serif" }}>
      <header style={{ marginBottom: 28 }}>
        <h1 style={{ marginBottom: 8 }}>{resolved.album.name}</h1>
        {resolved.album.description && <p style={{ color: "#555" }}>{resolved.album.description}</p>}
        <p style={{ color: "#777" }}>{images.length} {images.length === 1 ? "item" : "items"}</p>
      </header>
      {images.length === 0 ? (
        <p>This album does not contain any media.</p>
      ) : (
        <section style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 20 }}>
          {images.map((image) => {
            const mediaUrl = `/shared/${encodedToken}/media/${encodeURIComponent(image.id)}`;
            const downloadUrl = `/shared/${encodedToken}/download/${encodeURIComponent(image.id)}`;
            const isVideo = image.mime_type.startsWith("video/");
            return (
              <article key={image.id} style={{ border: "1px solid #ddd", borderRadius: 12, overflow: "hidden", paddingBottom: 12 }}>
                {isVideo ? (
                  <video src={mediaUrl} controls preload="metadata" style={{ width: "100%", aspectRatio: "1", objectFit: "cover" }} />
                ) : (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={mediaUrl} alt={image.original_name} loading="lazy" style={{ width: "100%", aspectRatio: "1", objectFit: "cover" }} />
                )}
                <div style={{ padding: "10px 12px 0" }}>
                  <div style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{image.original_name}</div>
                  <a href={downloadUrl} style={{ display: "inline-block", marginTop: 8 }}>Download</a>
                </div>
              </article>
            );
          })}
        </section>
      )}
    </main>
  );
}
