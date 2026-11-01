package com.nendo.argosy.libretro.coreoptions

interface CoreOptionManifest {
    val coreId: String
    val options: List<CoreOptionDef>
}
