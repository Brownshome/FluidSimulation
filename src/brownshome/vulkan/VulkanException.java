package brownshome.vulkan;

import static org.lwjgl.vulkan.VK10.*;

public class VulkanException extends RuntimeException {
	public VulkanException(String message) {
		super(message);
	}

	public VulkanException(int result) {
		this(translate(result));
	}

	public static String translate(int result) {
		switch(result) {
			case VK_SUCCESS: return "Command successfully completed";
			default: return "Unknown error";
		}

		/**
		 * SUCCESS Command successfully completed
		 * NOT_READY A fence or query has not yet completed
		 * TIMEOUT A wait operation has not completed in the specified time
		 * EVENT_SET An event is signaled
		 * EVENT_RESET An event is unsignaled
		 * INCOMPLETE A return array was too small for the result
		 * SUBOPTIMAL_KHR A swapchain no longer matches the surface properties exactly, but can still be used to present to the surface successfully.
		 * ERROR_OUT_OF_HOST_MEMORY A host memory allocation has failed.
		 * ERROR_OUT_OF_DEVICE_MEMORY A device memory allocation has failed.
		 * ERROR_INITIALIZATION_FAILED Initialization of an object could not be completed for implementation-specific reasons.
		 * ERROR_DEVICE_LOST The logical or physical device has been lost. See Lost Device
		 * ERROR_MEMORY_MAP_FAILED Mapping of a memory object has failed.
		 * ERROR_LAYER_NOT_PRESENT A requested layer is not present or could not be loaded.
		 * ERROR_EXTENSION_NOT_PRESENT A requested extension is not supported.
		 * ERROR_FEATURE_NOT_PRESENT A requested feature is not supported.
		 * ERROR_INCOMPATIBLE_DRIVER The requested version of Vulkan is not supported by the driver or is otherwise incompatible for implementation-specific reasons.
		 * ERROR_TOO_MANY_OBJECTS Too many objects of the type have already been created.
		 * ERROR_FORMAT_NOT_SUPPORTED A requested format is not supported on this device.
		 * ERROR_FRAGMENTED_POOL A pool allocation has failed due to fragmentation of the pool’s memory. This must only be returned if no attempt to allocate host or device memory was made to accomodate the new allocation. This should be returned in preference to ERROR_OUT_OF_POOL_MEMORY_KHR, but only if the implementation is certain that the pool allocation failure was due to fragmentation.
		 * ERROR_SURFACE_LOST_KHR A surface is no longer available.
		 * ERROR_NATIVE_WINDOW_IN_USE_KHR The requested window is already in use by Vulkan or another API in a manner which prevents it from being used again.
		 * ERROR_OUT_OF_DATE_KHR A surface has changed in such a way that it is no longer compatible with the swapchain, and further presentation requests using the swapchain will fail. Applications must query the new surface properties and recreate their swapchain if they wish to continue presenting to the surface.
		 * ERROR_INCOMPATIBLE_DISPLAY_KHR The display used by a swapchain does not use the same presentable image layout, or is incompatible in a way that prevents sharing an image.
		 * ERROR_INVALID_SHADER_NV One or more shaders failed to compile or link. More details are reported back to the application via VK_EXT_debug_report if enabled.
		 * ERROR_OUT_OF_POOL_MEMORY_KHR A pool memory allocation has failed. This must only be returned if no attempt to allocate host or device memory was made to accomodate the new allocation. If the failure was definitely due to fragmentation of the pool, ERROR_FRAGMENTED_POOL should be returned instead.
		 * ERROR_INVALID_EXTERNAL_HANDLE_KHR An external handle is not a valid handle of the specified type. 
		 */
	}
}
