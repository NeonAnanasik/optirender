{
  "schemaVersion": 1,
  "id": "optirender",
  "version": "${version}",
  "name": "OptiRender",
  "description": "Client-side rendering optimizations for Minecraft 26.3: occlusion culling, smart leaves, debug-overlay caching, allocation-free culling math.",
  "authors": ["OptiRender"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": ["dev.optirender.OptiRenderClient"]
  },
  "mixins": [
    { "config": "optirender.mixins.json", "environment": "client" }
  ],
  "depends": {
    "fabricloader": ">=0.20.1",
    "minecraft": "~26.3",
    "java": ">=25"
  }
}
